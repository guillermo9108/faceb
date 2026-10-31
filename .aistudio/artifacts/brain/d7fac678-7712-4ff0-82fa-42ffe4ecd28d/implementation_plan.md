# Plan de Trabajo: Grupos Familiares desde Carpetas del NAS

Plan arquitectónico y de implementación para permitir al Administrador configurar rutas de almacenamiento en el Synology NAS, explorar y seleccionar carpetas multimedia para convertirlas en Grupos Familiares, y sincronizar sus fotos y vídeos automáticamente en la base de datos MariaDB para que todos los familiares puedan ver y comentar.

## User Review & Critical Decisions

> [!IMPORTANT]
> Decisiones confirmadas a través de la fase de aclaración:
> - **Selección de Carpetas**: El administrador introduce una ruta base de almacenamiento del NAS (ej. `/volume1/photo/Familia` o `/volume1/homes/admin/Fotos`) y el servidor lista las carpetas detectadas para que el administrador elija selectivamente cuáles importar como grupos.
> - **Modo de Escaneo**: Escaneo y sincronización bajo demanda mediante botón administrativo en la app ("Escanear y Sincronizar"), evitando sobrecargar el CPU y discos del NAS de forma continua.
> - **Permisos y Participación**: Todos los miembros de la familia pueden visualizar las publicaciones del grupo, dar me gusta, comentar y disfrutar del contenido tanto en línea como guardado en la carpeta oculta `.offline_media`.

- **Confirmado 1**: El servidor PHP (`index.php`) expondrá endpoints de escaneo (`action=admin/scan_folders`) e importación (`action=admin/import_group`), ejecutables únicamente por la cuenta `admin`.
- **Confirmado 2**: El nombre de la carpeta seleccionada se convierte en el nombre del Grupo (ej. `Vacaciones 2024`, `Cumpleaños Abuela`, `Asados`).
- **Confirmado 3**: Los archivos de fotos (`.jpg`, `.png`, `.webp`) y vídeos (`.mp4`, `.mov`, etc.) contenidos dentro de cada carpeta se insertan en la tabla `posts` vinculados a un `group_id`, creando miniaturas y registros en la tabla `media`.

---

## 1. Overview & Core Concept

### Qué resuelve este módulo
Actualmente, las publicaciones familiares se crean de manera individual desde el móvil subiendo fotos y vídeos uno a uno. Muchas familias ya tienen años de recuerdos organizados en carpetas dentro de los discos duros de su Synology NAS. Este módulo permite conectar esas carpetas existentes directamente al muro familiar:
1. El Administrador ingresa una ruta del disco del NAS.
2. El servidor lista las subcarpetas encontradas junto con la cantidad de fotos y vídeos en cada una.
3. El Administrador selecciona qué carpetas desea convertir en Grupos Familiares y pulsa "Importar / Sincronizar".
4. El servidor crea los grupos en MariaDB, genera las publicaciones y miniaturas correspondientes, y los familiares disfrutan de sus álbumes agrupados con comentarios y reacciones.

---

## 2. User Experience & Visual Design

### Flujo de Usuario: Administrador
1. **Acceso al Panel de Grupos**: En el menú o pestaña de Ajustes del NAS, el Administrador tiene la sección *"Gestión de Grupos y Almacenamiento NAS"*.
2. **Configuración de Ruta**: Campo de texto para la ruta absoluta en el NAS (ej. `/volume1/photo` o `/volume1/family_albums`) con botón *"Explorar Carpetas"*.
3. **Selector de Carpetas**: Lista visual con tarjetas tipo carpeta (icono M3, nombre de la carpeta, número de fotos y vídeos detectados, tamaño aproximado y casilla de selección).
4. **Ejecución del Escaneo**: Botón principal *"Sincronizar Grupos Seleccionados"* con barra de progreso y diálogo de resumen (ej. `3 grupos actualizados, 48 publicaciones creadas`).

### Flujo de Usuario: Miembros Familiares
1. **Pestaña / Selector de Grupos**: En el Muro o mediante una nueva pestaña *"Grupos"*, los familiares ven carruseles o tarjetas de grupos activos con su foto de portada (la más reciente de la carpeta).
2. **Muro del Grupo**: Al entrar a un grupo, se muestra la cabecera con el nombre de la carpeta, fecha y miembros, seguido de la cuadrícula o feed de publicaciones pertenecientes a ese grupo.
3. **Interacción Total**: Cada publicación del grupo permite dar "Me gusta", comentar, reproducir vídeos y guardar en la caché local `.offline_media`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Sincronización Incremental (Evitar duplicados)**
  - *Enfoque*: El servidor registra la ruta de archivo relativa (`source_path`) de cada medio escaneado. En escaneos posteriores de la misma carpeta, solo se añaden los archivos nuevos que no existan previamente en MariaDB.
  - *Por qué*: Si el usuario añade 5 fotos nuevas a la carpeta del NAS, la sincronización tarda menos de un segundo y no duplica las publicaciones existentes.

- **Decisión 2: Enlace Simbólico / Ruta Web Station para Medios del NAS**
  - *Enfoque*: El servidor expone o sirve los medios escaneados a través de un controlador PHP (`index.php?action=media_proxy&path=...`) o copiando/creando enlaces a carpetas servibles por Web Station con miniaturas generadas en `uploads/thumbs/`.
  - *Por qué*: En Synology DSM, Web Station por defecto solo tiene acceso directo a `/volume1/web`. Con el proxy seguro de medios o enlaces de lectura, el servidor puede acceder a volúmenes compartidos como `/volume1/photo` sin requerir reconfiguraciones complejas de Apache/Nginx.

- **Decisión 3: Modo Offline Transparente**
  - *Enfoque*: Los grupos y sus publicaciones se cachean en la base de datos Room (`cached_groups`, `cached_posts`) y los medios se descargan progresivamente a `.offline_media`.
  - *Por qué*: Si la familia sale de casa o se apaga el NAS, los grupos y álbumes siguen viéndose en la app sin conexión.

---

## 4. Technical Architecture & Data Strategy

### Diagrama del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                       ANDROID APP                           │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │ AdminGroupScanDialog  │       │  GroupsFeedScreen     │  │
│  │ (Ruta NAS + Selector) │       │  (Muro por Grupo)     │  │
│  └───────────┬───────────┘       └───────────┬───────────┘  │
│              │                               │              │
│              ▼                               ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  FamilyRepository                     │  │
│  │   ┌─────────────────────┐   ┌─────────────────────┐   │  │
│  │   │ Room (cached_groups)│   │.offline_media Cache │   │  │
│  │   └─────────────────────┘   └─────────────────────┘   │  │
│  └───────────────────────────┬───────────────────────────┘  │
└──────────────────────────────┼──────────────────────────────┘
                               │ HTTP / JSON (LAN)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 SYNOLOGY NAS (WEB STATION)                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ index.php                                             │  │
│  │  ├── action=admin/scan_folders                        │  │
│  │  ├── action=admin/import_group                        │  │
│  │  ├── action=groups                                    │  │
│  │  └── action=group/posts                               │  │
│  └───────────┬───────────────────────────────┬───────────┘  │
│              │                               │              │
│              ▼                               ▼              │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │ MariaDB 10            │       │ Almacenamiento NAS    │  │
│  │ - groups              │       │ /volume1/photo/...    │  │
│  │ - posts (group_id)    │       │ /volume1/familia/...  │  │
│  │ - media               │       │                       │  │
│  └───────────────────────┘       └───────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 1. Esquema de Base de Datos (MariaDB & Room)

```sql
-- Tabla de Grupos Familiares
CREATE TABLE IF NOT EXISTS `family_groups` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    folder_path VARCHAR(255) NOT NULL,
    description TEXT NULL,
    cover_photo_url VARCHAR(255) NULL,
    created_by_user_id INT DEFAULT 1,
    posts_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_group_folder (folder_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Modificación en tabla posts para asociar a grupo
ALTER TABLE posts ADD COLUMN group_id INT NULL;
ALTER TABLE posts ADD INDEX idx_group_id (group_id);
```

### 2. Endpoints del Backend PHP (`index.php`)

1. **`GET index.php?action=admin/scan_folders&base_path=/volume1/photo`**:
   - Valida que el usuario sea el Administrador.
   - Lee el directorio con `scandir()` o `DirectoryIterator`.
   - Por cada subcarpeta, cuenta archivos compatibles (`jpg`, `jpeg`, `png`, `webp`, `mp4`, `mov`, `mkv`) y tamaño total.
   - Retorna:
     ```json
     {
       "base_path": "/volume1/photo",
       "folders": [
         { "folder_name": "Vacaciones Playa", "relative_path": "Vacaciones Playa", "media_count": 42, "already_imported": false },
         { "folder_name": "Cumpleaños Abuelo", "relative_path": "Cumpleaños Abuelo", "media_count": 18, "already_imported": true }
       ]
     }
     ```

2. **`POST index.php?action=admin/import_group`**:
   - Recibe: `folder_path`, `group_name`, `admin_user_id`.
   - Crea el registro en `family_groups`.
   - Escanea los archivos de la carpeta e inserta los posts con `group_id`.
   - Genera miniaturas en `uploads/thumbs/` para fotos y vídeos.
   - Retorna resumen de importación.

3. **`GET index.php?action=groups`**:
   - Lista todos los grupos activos con su portada, cantidad de publicaciones y última actualización.

4. **`GET index.php?action=group/posts&group_id=X`**:
   - Retorna las publicaciones pertenecientes al grupo `X`.

### 3. Fases de Ejecución del Código

- **Fase A (Backend NAS & Base de Datos)**:
  - Actualizar `schema.sql` y `config.php` con la tabla `family_groups` y la columna `group_id` en `posts`.
  - Implementar en `index.php` los controladores `admin/scan_folders`, `admin/import_group`, `groups` y filtro de grupo en `feed`.
  - Actualizar el generador del ZIP de configuración con los nuevos scripts.
- **Fase B (Capa de Datos Android & Caché)**:
  - Crear entidad Room `GroupEntity`, DAO `GroupDao` y migración de base de datos local.
  - Añadir métodos en `NasApiClient` y `FamilyRepository` para listar carpetas del NAS, importar grupos y sincronizar publicaciones grupales.
- **Fase C (Interfaz de Usuario Jetpack Compose)**:
  - Diálogo administrativo en Ajustes para ingresar la ruta del NAS, previsualizar subcarpetas detectadas con casillas de verificación y botón de escaneo/importación.
  - Pestaña o carrusel de Grupos Familiares en la interfaz principal.
  - Pantalla detallada de Grupo con visualización de feed, fotos, vídeos, likes y comentarios.
- **Fase D (Pruebas Automatizadas & Compilación)**:
  - Pruebas unitarias con Robolectric para validar el parseo de carpetas, creación de grupos, exclusión de duplicados y persistencia offline.
  - Verificación final de compilación.
