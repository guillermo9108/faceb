# Plan de Trabajo: Mejoras de Configuración NAS, Exportación ZIP y Publicaciones Estilo Facebook

Mejora integral de la aplicación **Muro Familiar LAN** para incorporar configuración visual de `config.php`, exportación de paquete ZIP listo para desplegar en Synology NAS, y renovación visual completa de las publicaciones con mosaicos multimagen y visor en pantalla dividida (imagen + comentarios simultáneos) estilo Facebook.

---

### Decisiones Confirmadas del Usuario

> [!IMPORTANT]
> - **Organización de Fotos en el Muro**: Mosaico dinámico estilo Facebook (1 a 4+ fotos con tamaños adaptativos y contador `+N` en la última imagen).
> - **Visor de Foto en Detalle**: Pantalla dividida con imagen en alta resolución y panel de comentarios/reacciones interactivo simultáneo.
> - **Herramienta de Configuración y ZIP**: Sección dedicada e interactiva dentro de la pantalla de **Ajustes del NAS** para editar `config.php` en vivo y descargar el ZIP completo listo para Web Station.
> - **Arquitectura de Base de Datos**: Confirmación de que la APK consume MariaDB 10 a través de la capa REST API en PHP 7.3/8 (`index.php` en Web Station), garantizando seguridad y compatibilidad sin requerir conexiones JDBC directas ni puertos abiertos a internet.

---

## 1. Visión General y Objetivos

1. **Gestor Visual de `config.php` y Exportador ZIP**:
   - Permitir al usuario inspeccionar, editar y copiar los parámetros de MariaDB 10 (`db_host`, `db_port`, `db_name`, `db_user`, `db_pass`).
   - Botón directo para descargar o compartir el archivo `.zip` que incluye `config.php` preconfigurado, `index.php`, `schema.sql`, `.htaccess` y la guía de instalación para Synology.
2. **Publicaciones Multimágenes en el Servidor y la APK**:
   - Permitir seleccionar múltiples fotos simultáneamente desde el selector de medios de Android (`PickMultipleVisualMedia`).
   - El backend PHP (`index.php`) procesará arreglos de fotos (`photo_0`, `photo_1`... o `photos[]`), generando miniaturas webp y asociándolas al mismo `post_id`.
3. **Mosaico Dinámico de Publicaciones Estilo Facebook**:
   - Tarjetas con bordes limpios, espaciado e interacciones idénticas a Facebook.
   - Distribución de medios según la cantidad de imágenes:
     - **1 imagen**: Formato completo con relación de aspecto natural (1:1, 4:5 o 16:9).
     - **2 imágenes**: Dos columnas simétricas divididas verticalmente.
     - **3 imágenes**: Una imagen principal destacada arriba y dos abajo.
     - **4 imágenes**: Mosaico 1 superior + 3 inferiores, o cuadrícula 2x2.
     - **5 o más imágenes**: Mosaico con overlay oscuro en la última imagen indicando cuántas fotos adicionales contiene (ej. `+3`).
4. **Visor de Foto en Pantalla Dividida (Facebook Split-View)**:
   - Al tocar cualquier foto, se abre un visor interactivo:
     - **Área Multimedia**: Fondo oscuro con navegación entre fotos, contador (`Foto 2 de 5`) y zoom táctil.
     - **Área Social**: Autor, texto de la publicación, barra de reacciones (Me gusta, Me encanta, etc.) y lista de comentarios en tiempo real con campo de texto para responder de inmediato.

---

## 2. Experiencia de Usuario y Diseño Visual

### Flujos Principales
```
[Pantalla Ajustes NAS]
   └──> [Sección Configuración Servidor]
           ├──> Campos editables (Host, Puerto 3307, BD, Usuario root, Contraseña)
           ├──> Vista previa en vivo del código config.php con botón "Copiar"
           └──> Botón "Descargar ZIP Completo para NAS" (Action Send / Save Intent)

[Crear Publicación]
   └──> Selector de imágenes múltiple (hasta 10 fotos)
           ├──> Tira horizontal de previsualización con botón (×) para remover
           └──> Subida concurrente al NAS mediante index.php

[Muro Familiar]
   └──> Tarjeta de Publicación Facebook
           ├──> Encabezado (Avatar, Nombre, Hora relativa, Ícono privacidad)
           ├──> Texto de la publicación
           ├──> Mosaico dinámico adaptativo (1, 2, 3, 4, 5+ con insignia +N)
           ├──> Barra social (Reacciones agrupadas, contador de comentarios)
           └──> Botones de acción: "Me gusta" (con selector de reacciones) y "Comentar"

[Al pulsar una imagen]
   └──> Visor Pantalla Dividida (Facebook Split-View)
           ├──> Superior / Izquierda: Visor de foto en negro con selector deslizable
           └──> Inferior / Derecha: Perfil, texto, reacciones y comentarios en vivo
```

### Paleta y Estilo
- **Azul Facebook**: `#1877F2` (acciones primarias, botones, badges).
- **Fondos**: Superficie blanca/modo oscuro con elevación suave de 0.5.dp y divisores sutiles (`#E4E6EB`).
- **Insignia `+N`**: Capa negra semitransparente (`Color.Black.copy(alpha = 0.55f)`) con tipografía bold blanca de 22.sp centrada.

---

## 3. Decisiones de Producto y Arquitectura Técnica

### Conexión Segura APK -> PHP -> MariaDB 10
```
┌─────────────────────────────────┐
│        Android APK (Muro)       │
│  - OkHttp 4 / Retrofit          │
│  - Jetpack Compose M3           │
└────────────────┬────────────────┘
                 │ HTTP REST (LAN / Wi-Fi)
                 │ Acciones: post, feed, comments, react, delete
                 ▼
┌─────────────────────────────────┐
│      Synology Web Station       │
│  - Nginx / Apache FastCGI       │
│  - index.php + config.php       │
└────────────────┬────────────────┘
                 │ PDO MySQL (Localhost 127.0.0.1:3307)
                 │ Socket / TCP interno
                 ▼
┌─────────────────────────────────┐
│           MariaDB 10            │
│  - Base de datos: family_feed   │
│  - 8 Tablas: users, posts,      │
│    media, comments, reactions,  │
│    stories, family_groups...    │
└─────────────────────────────────┘
```
- **Por qué**: La APK nunca debe exponer claves de root de MariaDB en la red ni requerir que MariaDB acepte conexiones remotas inseguras (que por defecto DSM deshabilita). Toda la autenticación, subida de archivos y consultas SQL se realizan de forma segura en el servidor mediante `index.php`.

---

## 4. Plan de Implementación por Fases

### Fase 1: Backend PHP y Generador de Scripts (`config.php` y ZIP)
1. **Actualizar `synology_server/index.php`**:
   - Ampliar la ruta `action=post` para recibir múltiples archivos en `$_FILES` (`photos[]` o `photo_0`, `photo_1`...).
   - Guardar cada imagen en `uploads/originals/`, generar miniatura en `uploads/thumbs/` e insertar cada registro en `media`.
2. **Actualizar `SynologyZipExporter.kt` y `SynologyScripts.kt`**:
   - Sincronizar el contenido del ZIP generado en tiempo real con las credenciales activas del usuario (`dbHost`, `dbPort`, `dbName`, `dbUser`, `dbPass`).
   - Crear función `generateZipFile(context, nasConfig): File` que permita descarga local o apertura en el explorador de archivos.

### Fase 2: Sección de Configuración en `NasConfigScreen.kt`
1. **Card "Generador de config.php y Paquete ZIP"**:
   - Campos interactivos para usuario y clave de MariaDB.
   - Diálogo / visor de código con resaltado de sintaxis para `config.php`.
   - Botón directo para compartir/guardar el archivo `family_feed_synology_setup.zip`.

### Fase 3: Selección Multimágen en `CreatePostDialog.kt`
1. Reemplazar `PickVisualMedia` simple por `PickMultipleVisualMedia(maxItems = 10)`.
2. Tira de vista previa con miniaturas de las imágenes seleccionadas y botón de eliminación individual.
3. Actualizar `NasApiClient.createPost` y `FamilyRepository.createPost` para enviar la lista de URIs en el `MultipartBody`.

### Fase 4: Mosaico de Publicaciones Estilo Facebook en `PostCard.kt`
1. Crear el composable `FacebookMediaGrid`:
   - Cálculo de cuadrícula según `mediaItems.size`:
     - 1 item: `SingleMediaView` con relación de aspecto dinámica.
     - 2 items: `TwoMediaGrid` (50% / 50%).
     - 3 items: `ThreeMediaGrid` (1 arriba al 100%, 2 abajo al 50% cada una).
     - 4 items: `FourMediaGrid` (1 arriba grande, 3 abajo simétricas).
     - 5+ items: `FivePlusMediaGrid` con capa oscura y texto `+X` sobre la última celda.
2. Callback `onMediaClick: (index: Int) -> Unit` al pulsar cualquier imagen del mosaico.

### Fase 5: Visor de Foto en Pantalla Dividida (`FacebookMediaViewerDialog.kt`)
1. Implementar componente modal de pantalla completa con diseño adaptable:
   - **En modo vertical**: Mitad superior visor de fotos con paginador táctil horizontal (`HorizontalPager`) y fondo negro; mitad inferior panel scrollable con avatar, nombre, hora, descripción de la publicación, resumen de reacciones y lista de comentarios con caja de envío rápido.
   - **En modo horizontal / tableta**: Lado izquierdo visor de fotos (65%) y lado derecho panel de comentarios y reacciones (35%).
2. Integrar acciones en vivo: dar reacción directamente desde el visor y enviar comentarios que se actualizan al instante.

### Fase 6: Verificación y Pruebas
1. Compilar el proyecto con `compile_applet`.
2. Ejecutar la suite de pruebas unitarias (`gradle :app:testDebugUnitTest`).
3. Verificar la integridad de los archivos ZIP generados y la respuesta de los endpoints PHP.
