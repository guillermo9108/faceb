# Plan de Trabajo: Fragmentación y Subida Resiliente de Vídeos con Reproducción Continua

Plan arquitectónico y de desarrollo para dividir vídeos en fracciones pequeñas antes de subirlos al servidor Synology NAS, gestionar la subida progresiva y tolerante a fallos, y reproducir los fragmentos en la aplicación como un solo vídeo continuo e ininterrumpido en el Muro y en Shorts.

---

## 1. Visión General del Sistema y Objetivos

### Problema que Resuelve
En redes locales hogareñas o conexiones Wi-Fi con interferencias:
- Los vídeos grandes de alta resolución (1080p, 4K o vídeos largos) tardan en subirse y pueden fallar por cortes temporales de red o límites de memoria/tiempo de ejecución de PHP.
- Si falla la subida al 95%, el usuario se ve obligado a reiniciar toda la carga desde cero.
- Al dividir el vídeo en fracciones pequeñas (ej. segmentos de 6 a 10 segundos alineados por fotogramas clave / I-frames o fragmentos binarios con metadatos):
  1. Cada fragmento se sube de manera independiente con reintento automático si uno solo falla.
  2. La subida muestra un progreso visual claro por fragmento.
  3. La reproducción en el dispositivo receptor puede comenzar de inmediato cargando el primer fragmento y encadenando los siguientes sin pausas ni cortes perceptibles.

---

## 2. Arquitectura Técnica

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DISPOSITIVO ANDROID                             │
│                                                                        │
│  [ Vídeo Original ]                                                    │
│          │                                                             │
│          ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ VideoChunkingManager                                             │  │
│  │ - Análisis de duración y puntos clave (MediaExtractor / Keyframe)│  │
│  │ - Fragmentación en segmentos MP4 independientes (6-10s)         │  │
│  │ - Generación de miniatura inicial y manifiesto local             │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ChunkedUploadManager (OkHttp LAN)                                │  │
│  │ - Subida secuencial/paralela de fragmentos: chunk_001.mp4, etc.  │  │
│  │ - Control de reintentos por fragmento sin perder los ya subidos  │  │
│  │ - Envío del manifiesto final de publicación / short              │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │ HTTP POST (action=upload_chunk)
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       SYNOLOGY NAS (WEB STATION)                       │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ index.php                                                        │  │
│  │  ├── action=video/upload_chunk                                   │  │
│  │  │   Guarda fragmentos en uploads/chunks/{session_id}/           │  │
│  │  ├── action=video/finalize_chunks                                │  │
│  │  │   Registra el post y los fragmentos en MariaDB 10             │  │
│  │  │   Opcional: Fusión de archivo o generación de lista M3U8/JSON │  │
│  │  └── action=media_proxy                                          │  │
│  │      Streaming con soporte de rangos y caché                     │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ MariaDB 10                                                       │  │
│  │ - posts (id, user_id, is_short, total_duration)                  │  │
│  │ - media (post_id, media_type='VIDEO', file_path, is_chunked)     │  │
│  │ - media_chunks (media_id, chunk_index, file_path, duration_ms)   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│               REPRODUCCIÓN CONTINUA SIN INTERRUPCIONES                 │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ SeamlessVideoPlayer (Media3 / ExoPlayer / VideoSequencePlayer)   │  │
│  │ - Precarga del siguiente fragmento en buffer antes de que acabe  │  │
│  │   el actual (Zero-Gap Transition)                                │  │
│  │ - Barra de tiempo unificada (progreso total, no por fragmento)   │  │
│  │ - Integración transparente en PostCard (Feed) y ShortsScreen     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Fases de Implementación

### Fase 1: Base de Datos y Backend PHP en Synology NAS
1. **Esquema de MariaDB (`schema.sql` y `config.php`):**
   - Añadir la tabla `media_chunks`:
     ```sql
     CREATE TABLE IF NOT EXISTS media_chunks (
         id INT AUTO_INCREMENT PRIMARY KEY,
         post_id INT NOT NULL,
         chunk_index INT NOT NULL,
         total_chunks INT NOT NULL,
         file_path VARCHAR(500) NOT NULL,
         duration_ms INT DEFAULT 0,
         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
         INDEX (post_id),
         FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
     ```
   - Añadir columnas en `media`: `is_chunked TINYINT DEFAULT 0`, `chunks_manifest TEXT NULL`.
2. **Nuevos Endpoints en `index.php`:**
   - `action=video/upload_chunk`: Recibe fragmentos individuales (`chunk_index`, `total_chunks`, `session_id`, `chunk_file`).
   - `action=video/finalize_chunks`: Crea la publicación final (en el Muro o en Shorts), vincula los fragmentos en `media_chunks` y genera la miniatura principal.
   - Soporte en `action=feed` y `action=shorts` para incluir la lista ordenada de URLs de fragmentos de cada vídeo.

### Fase 2: Motor de Fragmentación en Android (`VideoChunker`)
1. **Módulo de corte de vídeo (`VideoChunkingManager.kt`):**
   - Utilizar las APIs nativas de Android (`MediaExtractor` y `MediaMuxer`) para segmentar el archivo de vídeo en tramos de 6 a 10 segundos.
   - Sincronización en Keyframes (I-frames): los cortes se realizan en puntos de sincronización natural para que cada fragmento sea un archivo MP4 válido e independiente sin necesidad de recodificar todo el vídeo (proceso casi instantáneo que no recalienta el móvil ni agota la batería).
   - Generación de póster/miniatura representativa en el fragmento 0.
2. **Caché temporal local:**
   - Almacenar los fragmentos en la caché de la app (`cacheDir/chunks/{uuid}/`) hasta que se complete la subida, liberando espacio tras finalizar con éxito.

### Fase 3: Subida Resiliente con Progreso Detallado (`ChunkedUploadManager`)
1. **Lógica de red en `NasApiClient.kt`:**
   - Subida secuencial con control de flujo: si el fragmento 3 falla, se reintenta automáticamente hasta 3 veces sin reiniciar los fragmentos 1 y 2.
   - Notificación de progreso reactivo: porcentaje global, fragmento actual (ej. `3 de 7`) y tiempo restante estimado.
2. **Integración en `CreatePostDialog.kt`:**
   - Indicador visual animado de fragmentación previa y barra de progreso paso a paso.
   - Soporte idéntico para publicaciones normales del Muro y para subidas clasificadas como Shorts.

### Fase 4: Reproducción Fluida sin Pausas (Seamless Playback)
1. **Componente de Reproducción en Secuencia (`SeamlessVideoPlayer`):**
   - Encadenamiento de fragmentos mediante cola de medios con pre-buffering del siguiente segmento mientras se reproduce el actual.
   - Al llegar al final de `part_001.mp4`, el reproductor conmuta de inmediato a `part_002.mp4` sin pantalla negra ni congelamiento.
2. **Barra de Progreso y Duración Unificada:**
   - La interfaz muestra la duración total del vídeo (suma de todos los fragmentos).
   - El control de avance/retroceso (seek) mapea el segundo seleccionado al fragmento correspondiente de forma transparente.
3. **Integración en Shorts (`ShortsScreen.kt`) y Muro (`PostCard.kt`):**
   - En Shorts: precarga inmediata del primer fragmento de los siguientes vídeos para inicio instantáneo vertical.
   - En Tarjetas del Muro: reproducción en la misma tarjeta o en el modal ampliado.

### Fase 5: Caché Offline de Fragmentos
1. **Gestión en `.offline_media`:**
   - Adaptación de `OfflineMediaManager` para almacenar los fragmentos descargados con su nombre de sesión, permitiendo la reproducción ininterrumpida sin conexión a internet o cuando el Synology NAS esté apagado.

---

## 4. Pruebas y Validación

1. **Pruebas Unitarias y Robolectric:**
   - Cálculo correcto de fragmentos y límites de duración.
   - Validación del nuevo endpoint de subida por fragmentos en `index.php`.
   - Compatibilidad del esquema de base de datos en MariaDB 10.
2. **Pruebas de Red y Resiliencia:**
   - Simulación de corte de conexión a mitad de subida (verificar reanudación solo de las partes faltantes).
   - Verificación de ausencia de pausas visuales o auditivas en el cambio entre fragmentos.

---

## 5. Preguntas Clave para el Usuario

1. **Duración de los fragmentos:** ¿Prefieres segmentos de aproximadamente 6 a 8 segundos (óptimo para streaming rápido y bajo consumo de RAM) o fragmentos de 15 segundos?
2. **Fusión en el servidor:** ¿Deseas que el Synology NAS intente unir los fragmentos en un único archivo MP4 en segundo plano además de conservar la lista de fragmentos para streaming rápido?
