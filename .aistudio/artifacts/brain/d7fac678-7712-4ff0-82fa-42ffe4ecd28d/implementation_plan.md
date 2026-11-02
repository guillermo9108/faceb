# Plan de Trabajo: Fluidez Multimedia Total, Optimización de Red Offline y Progreso de Fragmentación/Subida

## 1. Visión y Objetivos Principales (Completados)
Este plan de trabajo implementó de forma unificada tres pilares críticos de la experiencia multimedia en la app familiar y su sincronización con el Synology NAS:
1. **Fluidez Extrema en Reproducción de Vídeo (Feed, Shorts y Diálogos)**:
   - Migración de `VideoView` tradicional a **AndroidX Media3 ExoPlayer** con pre-buffering inteligente y concatenación nativa de fragmentos (gapless playback sin cortes ni parpadeos negros).
   - Reutilización de reproductor compartido / pool de ExoPlayer para evitar la sobrecarga de crear y destruir instancias al hacer scroll vertical en Feed y Shorts.
2. **Sistema Offline Inteligente y Protección de Red en Grupos**:
   - Ajustada la política de descarga automática del `OfflineMediaManager` para **descargar únicamente miniaturas y el primer fragmento (preview chunk)** de cada vídeo.
   - Protección total del ancho de banda WiFi/LAN y del almacenamiento local del dispositivo al sincronizar o explorar carpetas del NAS con archivos pesados.
   - El usuario reproduce al instante el inicio de cualquier vídeo sin conexión, requiriendo descarga bajo demanda para el resto del metraje si desea guardarlo completo.
3. **Flujo de Fragmentación y Subida con Progreso Dual Secuencial**:
   - Medición y emisión de progreso real durante la división de vídeo por I-frames en `VideoChunkingManager` mediante callback (`onProgress: (Float, Int, Int)`).
   - Interfaz de usuario con **dos barras de progreso secuenciales**:
     - **Etapa 1**: "Fragmentando vídeo (X%) - Fragmento Y de Z..."
     - **Etapa 2**: "Subiendo fragmentos al servidor (X%) - Enviando fragmento Y de Z...".

---

## 2. Decisiones de Arquitectura e Integración Técnica (Implementadas)

### A. Reproducción Multimedia Fluida (Media3 ExoPlayer)
- **Dependencias**: Incorporadas `androidx.media3:media3-exoplayer:1.5.1` y `androidx.media3:media3-ui:1.5.1` en `libs.versions.toml` y `app/build.gradle.kts`.
- **Gapless Chunk Concatenation**: Utilizado `player.setMediaItems(listOfMediaItems)` para encolar todos los fragmentos `chunk_0000.mp4`, `chunk_0001.mp4`, etc. ExoPlayer precarga el siguiente fragmento en memoria mientras reproduce el actual, eliminando por completo cualquier pausa entre tramos.
- **Cache Local Directo**: Conectado ExoPlayer con los URIs provistos por `OfflineMediaManager.getPlayableMediaUri(context, url)`, aprovechando de forma transparente cualquier fragmento ya guardado en el almacenamiento oculto `.offline_media`.
- **ExoPlayer Component**: Creado `FamilyVideoPlayer.kt` reutilizado en `PostCard`, `ShortsScreen` y `VideoPlayerDialog`.

### B. Optimización de Red Offline (Solo Miniaturas y Primer Fragmento)
- **Modificación en `OfflineMediaManager.prefetchPostsMedia`**:
   - Filtradas las URLs para **únicamente encolar la miniatura (`thumbnailUrl`) y el primer fragmento** (`chunks.firstOrNull() ?: media.url`).
   - Evita la saturación del router y de la red local del NAS ante grupos grandes con vídeos extensos.
   - Implementado método `downloadFullPostMedia` para descarga completa bajo demanda.

### C. Sistema de Progreso de Fragmentación y Subida Dual
- **`VideoChunkingManager`**: Reporta progreso en tiempo real durante la extracción y empaquetado muxer de cada fragmento basado en los microsegundos procesados vs duración total (`ptsUs / totalDurationUs`).
- **`FamilyRepository`**: Coordina el progreso entre la etapa de corte y la etapa de subida HTTP de cada fragmento con `VideoUploadProgress`.
- **`VideoUploadProgressDialog`**: Renderiza el panel de progreso dual secuencial con diseño limpio Material 3 mostrando ambas etapas.

---

## 3. Estado de Ejecución: COMPLETADO
- [x] Fase 1: Dependencias Media3 ExoPlayer añadidas y configuradas en el proyecto.
- [x] Fase 2: Componente `FamilyVideoPlayer` implementado e integrado en Feed (`PostCard`), `ShortsScreen` y `VideoPlayerDialog`.
- [x] Fase 3: Política de descarga offline ligera (miniaturas + primer fragmento) en `OfflineMediaManager`.
- [x] Fase 4: Progreso secuencial dual de fragmentación y subida en `VideoChunkingManager`, `FamilyRepository` y `VideoUploadProgressDialog`.
- [x] Fase 5: Validación y compilación exitosa del proyecto con Gradle y `compile_applet`.
