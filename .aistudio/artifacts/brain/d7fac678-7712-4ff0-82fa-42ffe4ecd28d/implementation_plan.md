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

---

## 4. Diagnóstico en Profundidad: Compatibilidad PHP 7.3 & MariaDB 10 + Registro de Errores en la APK (Completado)

### A. Diagnóstico de Incompatibilidades con PHP 7.3 en Synology Web Station
1. **Ausencia de la expresión `match()`**:
   - `match()` fue introducido en PHP 8.0+. En PHP 7.3 causaba un error fatal sintáctico (`Parse error: syntax error, unexpected 'match'`).
   - Solución: Sustituido por `switch ($type)` y sentencias `if/else` estándar de PHP 7.3.
2. **Funciones nativas de cadenas (`str_starts_with`, `str_ends_with`, `str_contains`)**:
   - Funciones ausentes en PHP 7.3 (añadidas en PHP 8.0).
   - Solución: Incorporados polyfills seguros basados en `substr`, `strlen` y `strpos`.
3. **Manejo de Errores Fatales en PHP 7.3**:
   - En PHP 7.3, ante un `E_ERROR` o `ParseError`, el servidor retornaba respuestas vacías o HTML 500 ininteligible para la APK.
   - Solución: Añadido `register_shutdown_function` en `index.php` para interceptar `error_get_last()` y emitir siempre JSON con cabecera `Content-Type: application/json` y código HTTP 500.

### B. Particularidades y Auto-reparación en MariaDB 10 (Synology DSM)
1. **Puerto por defecto 3307**:
   - Synology DSM reserva el puerto 3306 para MySQL/MariaDB 5 del sistema; MariaDB 10 se ejecuta en el **puerto 3307**. Documentado en `SynologyZipExporter` e instrucciones de conexión.
2. **Ejecución aislada de sentencias DDL**:
   - Múltiples sentencias `CREATE TABLE` en una sola llamada `PDO::exec()` fallan si `MYSQL_ATTR_MULTI_STATEMENTS` no está activo en el driver PDO de PHP 7.3.
   - Solución: Función `autoBootstrapDatabase()` que ejecuta cada sentencia `CREATE TABLE IF NOT EXISTS` de forma individualizada y protegida con bloque `try/catch`.
3. **Endpoint de Auto-Reparación y Regeneración de Esquema**:
   - Creado endpoint dedicado `POST /index.php?action=repair_db` que reconstruye las tablas faltantes (`posts`, `users`, `reactions`, `comments`, `family_groups`, `media_chunks`, `stories`) e inserta usuarios iniciales si no existen.
   - El cliente Android detecta respuestas 500 con mensajes de tablas faltantes e invoca automáticamente la auto-reparación antes de alertar al usuario.

### C. Registro y Diagnóstico de Errores en la APK (`AppErrorLogger`)
1. **Motor de Logging Centralizado (`AppErrorLogger.kt`)**:
   - Almacenamiento persistente en JSON local (`app_error_logs.json`) de los últimos 200 eventos de error.
   - Categorización automática (`MARIADB`, `SYNOLOGY_API`, `NETWORK`, `VIDEO_CHUNKING`, `AUTH`, `SYSTEM`).
   - Detección heurística de soluciones recomendadas (ej. sugerir puerto 3307, activar extensión PDO MySQL, verificar tablas o firewall).
2. **Interfaz de Diagnóstico Integrada (`ErrorLogsDialog.kt`)**:
   - Acceso directo desde el banner de desconexión del Feed ("🔍 Ver registro de errores"), desde el menú lateral y desde la pantalla de configuración del NAS.
   - Filtro por categoría, visualización del cuerpo crudo de la respuesta del servidor, fecha/hora exacta y botón para **Copiar Informe Completo al portapapeles**.
   - Botón directo de **"Reparar MariaDB Ahora"** que ejecuta la sincronización de tablas en caliente.

---

## 5. Resolución Integral: Esquema MariaDB 10, Error 500 en Streaming, Aislamiento de Grupos y Modo Offline Ligero (Completado)

### A. Verificación del Esquema SQL vs Creación Automática
- Se verificó y alineó la creación de tablas en `synology_server/config.php` y `SynologyScripts.kt` con `schema.sql`:
  - `users`: Tipos de datos, columnas y usuario administrador inicial con contraseña hasheada.
  - `family_groups`: Clave foránea `uq_group_folder` y contador de posts.
  - `posts`: Claves foráneas e índices `idx_group` y `idx_is_short`.
  - `media` y `media_chunks`: Almacenamiento fraccionado y metadatos de duración y miniatura.
  - `reactions`: Clave primaria compuesta `PRIMARY KEY (post_id, user_id)` idéntica al `.sql`.
  - `stories`: Soporte completo de historias independientes.
  - Conexión multi-objetivo con fallback automático en `getDB()`: intenta primero TCP/IP en puerto 3307; si falla, conmuta automáticamente a los sockets de MariaDB 10 (`/run/mysqld/mysqld10.sock` y `/var/run/mysqld/mysqld10.sock`).

### B. Corrección de Error 500 en Reproducción de Vídeo (HTTP Range / 206 Partial Content)
- **Causa**: Al reproducir vídeos pesados desde el NAS, ExoPlayer emite cabeceras de rango (`Range: bytes=X-`). En PHP 7.3, la lectura con `readfile()` intentaba cargar el archivo completo en memoria, provocando agotamiento del `memory_limit` o tiempos de espera de Web Station (HTTP 500). Además, la ruta `media_proxy` no estaba presente en todos los scripts del servidor.
- **Solución**: Implementada la función `streamMediaFile()` con soporte nativo para `HTTP/1.1 206 Partial Content`, cálculo dinámico de `Content-Range`, vaciado de búfer de salida (`ob_end_clean`) y transmisión en bloques controlados de 64 KB (`fread/flush`).

### C. Corrección del Contenido Idéntico en Grupos
- **Causa**: La función `matchRoute` utilizaba `str_ends_with($action, '/posts')`, por lo que la petición `action=group/posts&group_id=X` era capturada por la ruta general de `feed` / `posts`, retornando todo el muro familiar sin filtrar por grupo.
- **Solución**:
  1. `matchRoute` ahora realiza comparación exacta en `$action === $target`.
  2. La ruta `group/posts` se posiciona de forma prioritaria con la cláusula SQL estricta `WHERE p.group_id = ?`.
  3. En `FamilyFeedViewModel.openGroup()`, se aísla inmediatamente el estado `_groupPosts` con filtrado en memoria y posterior reconciliación estricta por `groupId`.

### D. Optimización Total del Modo Offline (Cero Descargas Pesadas)
- **Causa**: `OfflineMediaManager.getPlayableMediaUri()` llamaba automáticamente a `cacheUrlAsync()` cada vez que se reproducía un vídeo, lo que desencadenaba la descarga completa de archivos pesados en segundo plano. Asimismo, `prefetchPostsMedia` descargaba el archivo de vídeo completo si este no venía fraccionado en partes.
- **Solución**:
  1. `getPlayableMediaUri()` ahora reproduce en streaming directo y nunca dispara descargas de fondo por sí mismo.
  2. `prefetchPostsMedia` se limita estrictamente a miniaturas y al primer fragmento (`chunk_0000.mp4`) en vídeos fragmentados. Los vídeos no fragmentados directos del NAS no se descargan automáticamente.
  3. `downloadAndCache()` cuenta con un límite de seguridad (`maxBytesLimit = 25MB`) que aborta cualquier descarga pesada en segundo plano a menos que el usuario solicite explícitamente guardar el post completo.

---

## 6. Eliminación de Acceso de Invitado y Escaneo LAN en Puertos 80 y 8080 (Completado)

### A. Eliminación del Botón de Invitado
- **Motivo**: En un muro social y familiar seguro sobre Synology NAS con MariaDB 10, las publicaciones, historias y comentarios deben pertenecer a miembros autenticados con su usuario, apodo, contraseña y avatar familiar.
- **Acción realizada**:
  - Eliminado el botón "Continuar como invitado (Acceso libre LAN)" en `LoginRegisterScreen.kt`.
  - Eliminado el callback y desvío de sesión anónima en `MainActivity.kt`.
  - Los miembros inician sesión con sus credenciales o se registran seleccionando su avatar y rol familiar.

### B. Escaneo Automático de IPs en Puertos 80 y 8080
- **Motivo**: Synology Web Station, contenedores Docker y configuraciones de Host Virtual a menudo sirven `index.php` en el puerto **8080** además o en lugar del puerto **80**. Si el escaneo se limitaba a un solo puerto, no encontraba el servidor NAS.
- **Acción realizada**:
  - En `NasApiClient.kt`: creado `probeNasServerOnPorts(ip, ports = listOf(80, 8080), path)` con sondeo rápido no bloqueante y timeout adaptativo (1000ms connect / 1500ms read), probando las rutas candidatas (`/family_api`, raíz `""`, `/family_feed`, `/api`).
  - En `FamilyRepository.kt`: implementado `autoDetectNasServer()` que sondea concurrentemente las IPs de todas las subredes locales detectadas del dispositivo en **ambos puertos (80 y 8080)**.
  - Implementado reintento cruzado inteligente en `testAndConfirmNasConfig`: si el usuario introduce una IP manualmente en el puerto 80 y la conexión es rechazada o agotada, el cliente prueba automáticamente en el puerto 8080 (y viceversa) para asistir al usuario en encontrar su Web Station sin fricción.
  - Actualizado el diálogo de confirmación `NasIpConfirmationDialog.kt` reflejando la búsqueda en los puertos 80 y 8080.

