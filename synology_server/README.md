# 🖥️ Configuración del Servidor en Synology NAS

Este directorio contiene los archivos necesarios para alojar el backend del **Muro Familiar LAN** en tu Synology NAS con **Web Station** y **MariaDB 10**.

---

## 📋 Pasos de Instalación en DSM (DSM 7 o DSM 6)

### 1. Instalar Paquetes en el Centro de Paquetes de DSM
1. **Web Station**
2. **PHP 8.2** (o 8.0/8.1)
3. **MariaDB 10**
4. **phpMyAdmin** (opcional, para gestionar la base de datos visualmente)

### 2. Crear la Base de Datos en MariaDB 10
- Abre phpMyAdmin o conéctate por terminal SSH a MariaDB (`mysql -u root -p -P 3307`).
- Ejecuta todo el contenido del archivo `schema.sql`.
- Esto creará la base de datos `family_feed` con sus 5 tablas (`users`, `posts`, `media`, `comments`, `reactions`) y los perfiles familiares iniciales.

### 3. Copiar los archivos a la carpeta Web
- En File Station, ve a la carpeta compartida `web`.
- Crea una subcarpeta llamada `family_api` (`/volume1/web/family_api`).
- Sube dentro de ella:
  - `config.php`
  - `index.php`
- Edita `config.php` y coloca tu contraseña de MariaDB 10:
  ```php
  define('DB_PASS', 'TuClaveMariaDB');
  ```

### 4. Configurar Web Station
- Ve a **Web Station** -> **Servicios web**.
- Crea un servicio o perfil PHP que tenga activadas las extensiones:
  - `pdo_mysql`
  - `gd` (para creación de miniaturas WebP)
  - `openssl`
- Ve a **Portal web** -> Crear portal:
  - Tipo: Servicio web
  - Raíz del documento: `web/family_api`
  - Puerto: HTTP personalizado (ej: `8085`)

### 5. Conectar la Aplicación Android
- En la aplicación Android, ve a la pestaña **Menú** -> **Configuración Servidor Synology NAS**.
- Introduce la IP de tu NAS en la red local (por ejemplo `192.168.1.150`), el puerto (`8085`) y la ruta (`/`).
- Pulsa **Probar Conexión**. Verás el indicador verde de estado en línea.
