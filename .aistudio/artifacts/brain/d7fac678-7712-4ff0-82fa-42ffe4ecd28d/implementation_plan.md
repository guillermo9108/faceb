# Plan de Mejora: Paginación del Muro, Modo Offline y Reproducción Multiparte Unificada

Plan de arquitectura y experiencia de usuario para cargar el muro de forma rápida (10 publicaciones por página al hacer scroll), reforzar el almacenamiento offline de publicaciones y miniaturas, optimizar la subida y reproducción de vídeos divididos en partes en toda la app y unificar la barra de progreso temporal para todos los fragmentos en un solo reproductor continuo.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Para avanzar directamente hacia la implementación sin bloqueos, el plan adopta las configuraciones recomendadas por defecto. Si prefieres ajustar algún comportamiento antes de comenzar, indícalo en tu revisión.

- **Estrategia de Caché Offline Automática (Recomendado)**: Al cargar cada bloque de 10 publicaciones, se descargan automáticamente las miniaturas, fotos del mosaico y metadatos (incluidos comentarios recientes) en la base local y carpeta oculta, mientras que los fragmentos pesados de vídeo se descargan solo al pulsar **"Guardar offline"** en cada publicación o al reproducirlos (evitando saturar la red Wi-Fi o el almacenamiento del teléfono).
- **Barra de Progreso Unificada para Vídeos por Partes (Recomendado)**: El reproductor suma la duración real de todos los fragmentos registrados en el servidor para mostrar una **línea de tiempo única y continua** (de `00:00` al total real del vídeo), con marcas discretas en las transiciones entre partes y salto instantáneo a cualquier segundo sin importar en qué fragmento esté.
- **Límite de Caché Multimedia**: Se propone un tope inteligente de almacenamiento offline configurables en Ajustes (por defecto 1 GB con limpieza automática de archivos más antiguos no fijados como favoritos offline).

---

## 1. Overview & Core Concept

### Qué resuelve este plan
Actualmente el muro solicita hasta 50 publicaciones de golpe en una sola consulta SQL, pierde parte de las fotos múltiples y fragmentos cuando no hay red, y los vídeos divididos en partes muestran la duración por fragmento separado en lugar de un único vídeo continuo —además de omitirse los fragmentos en secciones como Shorts.

### Beneficio principal para la familia
- **Apertura instantánea del muro**: Solo se descargan 10 publicaciones iniciales y las siguientes 10 se solicitan automáticamente cuando el usuario se acerca al final del scroll.
- **Experiencia offline fiel**: Las publicaciones ya vistas (incluyendo mosaicos de varias fotos, miniaturas de vídeos y comentarios) permanecen disponibles sin conexión a la red doméstica.
- **Reproducción unificada sin costuras**: Tanto en el muro, Watch, Shorts, Grupos, visor multimedia o pantalla completa, todos los vídeos fraccionados funcionan como un único vídeo con una sola barra continua de progreso.

---

## 2. User Experience & Visual Design

### Flujos Principales

1. **Carga Paginada en el Muro (10 en 10)**
   - Al abrir el muro se muestran inmediatamente las publicaciones guardadas offline en Room mientras en segundo plano se solicitan las primeras **10 publicaciones** más recientes del NAS.
   - Al desplazarse hacia abajo (cuando faltan 3 tarjetas para llegar al final), aparece un indicador compacto en la parte inferior ("Cargando 10 publicaciones más...") y se anexan las siguientes 10 sin perder la posición ni recargar las tarjetas superiores.
   - Si se llega al final del historial, se muestra un mensaje sutil ("Has visto todas las publicaciones familiares").
   - El gesto de deslizar hacia abajo desde arriba (*Pull-to-Refresh*) reinicia la página 1 trayendo las 10 más recientes.

2. **Guardado y Lectura Offline Mejorados**
   - Cada tarjeta incluye un indicador discreto de estado offline y un acceso en el menú de opciones / barra superior de tarjeta para **"Guardar completo para offline"** (descarga las fotos en alta calidad y todos los fragmentos del vídeo asociado).
   - Al cargar cada página de 10 publicaciones en red LAN, se pre-cachean de forma progresiva y ordenada sus miniaturas locales para que el scroll offline sea fluido y sin espacios en blanco.
   - Si la app detecta que el NAS está desconectado, muestra un banner informativo no intrusivo ("Modo sin conexión · Mostrando recuerdos guardados") y carga las publicaciones locales paginadas de 10 en 10 desde la base de datos interna.

3. **Barra Unificada Continua en Reproducción Multiparte**
   - **Alineación Visual Material 3**: Mantiene la estética limpia del tema actual con acento azul (`FacebookBlue`) sobre controles translúcidos oscuros de alto contraste y tipografía tabular para los contadores de tiempo (`01:24 / 04:15`).
   - **Comportamiento del Scrubber**:
     - Al arrastrar o tocar cualquier punto de la barra unificada, se calcula el fragmento destino y el desplazamiento exacto dentro de ese fragmento.
     - Opcionalmente sobre la pista de la barra aparecen finas muescas divisorias que representan las uniones entre fragmentos sin interrumpir el arrastre continuo.
     - Un único selector inferior con botón Play/Pausa, barra continua, tiempo transcurrido/total unificado, indicador discreto de calidad/partes (`⚡ 5 partes`) y botón de pantalla completa funciona idénticamente en tarjetas del muro, Watch, Grupos, Shorts, visor dividido y diálogo a pantalla completa.

---

## 3. Key Product Decisions & Trade-Offs

- **Paginación por Página/Offset con Agrupación SQL Limpia**
  - *Enfoque elegido*: Calcular la paginación (`LIMIT 10 OFFSET N`) sobre los `id` de publicaciones primero y luego adjuntar todas sus fotos, comentarios previos y fragmentos ordenados en una estructura agrupada por publicación.
  - *Por qué*: Actualmente, la unión directa con la tabla multimedia multiplica las filas cuando una publicación tiene varias fotos (por ejemplo, una publicación con 4 fotos consume 4 espacios del límite). Separar la paginación por publicación garantiza exactamente 10 tarjetas reales por página y permite devolver los mosaicos completos sin duplicados.

- **Línea de Tiempo Unificada Calculada en Cliente + Metadatos de Duración en Servidor**
  - *Enfoque elegido*: Registrar la duración real en milisegundos (`duration_ms`) de cada fragmento durante el corte en Android, guardarla en `media_chunks` al finalizar la subida y devolver la lista ordenada de `(url, duration_ms)` en el feed. En el cliente, el reproductor construye un mapa acumulado `[inicioMs..finMs]` por fragmento sobre ExoPlayer.
  - *Por qué*: Evita esperar a que ExoPlayer descargue las cabeceras de todos los fragmentos remotos antes de conocer la duración total real y permite saltar de inmediato al minuto 03:20 aunque solo se haya reproducido el primer fragmento.
  - *Alternativa descartada*: Unir físicamente todos los MP4 en el NAS con FFmpeg (muchos modelos Synology carecen de FFmpeg moderno o saturan la CPU del NAS y anularían la ventaja de transmisión ligera por fragmentos).

- **Serialización Completa del Mosaico y Comentarios en Caché Local (Room)**
  - *Enfoque elegido*: Ampliar la entidad local de publicaciones para persistir la lista completa de elementos multimedia (`mediaItemsJson`), un resumen de comentarios recientes y bandera explícita de descarga offline completa, usando actualización incremental por páginas en lugar de borrar la caché completa.
  - *Por qué*: Corrige la pérdida de fotos secundarias en publicaciones con múltiples imágenes al quedarse sin red y permite abrir el visor dividido con comentarios incluso offline.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CAPA DE PRESENTACIÓN (UI)                       │
│  FeedScreen / WatchScreen / GroupsScreen / ShortsScreen / MediaViewer   │
│         │                                          │                    │
│         ▼ (Scroll cerca del final: +10)            ▼ (Reproducción)     │
│  Paginación Infinita                        UnifiedMediaPlayer +        │
│  Estado Offline Badge                       UnifiedScrubberBar          │
└─────────┬──────────────────────────────────────────┬────────────────────┘
          │                                          │
          ▼                                          ▼
┌─────────────────────────────┐    ┌──────────────────────────────────────┐
│    ViewModel & Repositorio  │    │  Gestor de Fragmentos & Offline      │
│  - PagingState (offset=10)  │◄──►│  - VideoChunkingManager (duracionMs) │
│  - Sync incremental a Room  │    │  - TimelineMapper (globalMs ↔ chunk) │
│  - Cola de descarga offline │    │  - OfflineMediaManager (LRU + thumbs)│
└─────────┬───────────────────┘    └─────────────────┬────────────────────┘
          │                                          │
          ▼                                          ▼
┌─────────────────────────────┐    ┌──────────────────────────────────────┐
│    Caché Local (Room)       │    │     API PHP Synology Web Station     │
│  - PostEntity (v6: JSON     │    │  - GET feed?limit=10&offset=N        │
│    mosaico, chunks y        │    │    (agrupado por post + chunks_meta) │
│    comentarios offline)     │    │  - POST video/finalize_chunks        │
└─────────────────────────────┘    │    (persiste duration_ms por parte)  │
                                   └──────────────────────────────────────┘
```

### Mejoras Adicionales Detectadas en Auditoría Técnica

Durante la revisión del proyecto se identificaron varios fallos latentes que este plan corregirá en conjunto:

1. **Sincronización Backend de Fragmentos**: El script principal del servidor PHP omitía devolver las rutas de fragmentos (`chunks_paths`) en las consultas del muro, grupos y shorts, impidiendo la reproducción multiparte fuera de la carga de prueba inicial. Se unificará la respuesta en servidor y en el generador integrado de scripts de la app.
2. **Duplicación de Tarjetas con Múltiples Fotos**: La consulta SQL actual enlaza directamente las tablas `posts` y `media`, generando múltiples entradas JSON para una sola publicación con varias fotos. Se agruparán todos los ítems multimedia dentro de un arreglo `media_items` por publicación.
3. **Soporte Multiparte y Offline en Shorts y Visor Dividido**: La pantalla de Shorts y el visor de fotos estilo Facebook utilizaban un reproductor aislado que ignoraba los vídeos divididos en fragmentos y la caché local offline; ambos pasarán a usar el componente unificado con soporte completo de fragmentos y barra continua.
4. **Subida Resiliente de Vídeos Grandes**: Se registrará la duración exacta de cada fragmento durante el corte, se optimizará la tolerancia a fallos por fragmento (reintento exponencial sin repetir fragmentos ya confirmados por `session_id`) y se corregirán los sellos temporales (`PTS`) en cortes consecutivos de audio/vídeo para eliminar micro-cortes entre partes.
5. **Control de Caché Offline y Pre-carga Ordenada**: Se limitará la pre-carga automática de red al bloque visible (10 elementos por turno) priorizando siempre las miniaturas y evitando duplicar descargas al hacer scroll rápido.
