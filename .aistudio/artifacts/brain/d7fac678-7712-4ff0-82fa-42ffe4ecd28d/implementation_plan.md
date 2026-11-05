# Plan de Trabajo: Diagnóstico y Solución Integral del Error 500 y Conexión MariaDB 10 en Synology NAS

Plan de trabajo exhaustivo para corregir el error de conexión HTTP 500 entre el servidor PHP 7.3 en Synology Web Station y MariaDB 10. Implementa un motor de diagnóstico en vivo que evalúa sockets, TCP, extensiones y permisos, corrige la representación visual de la dirección de base de datos a `root@127.0.0.1:3307` en la app, y proporciona una configuración clara y resiliente en `config.php` y `index.php`.

---

## Decisiones Críticas y Preferencias del Usuario

> [!IMPORTANT]
> Decisiones confirmadas por el usuario para la resolución de la conexión:
> - **Gestión de Credenciales**: El usuario editará directamente las credenciales (`DB_USER`, `DB_PASS`, `DB_NAME`, `DB_PORT`) en el archivo `config.php` del NAS. El archivo vendrá documentado con comentarios claros y valores por defecto adaptados a Synology MariaDB 10.
> - **Diagnóstico Exhaustivo en Vivo**: Se integrará un endpoint especializado en PHP (`action=db_diagnostics`) y una vista de diagnóstico en vivo en la aplicación Android que probará paso a paso: extensión PDO, conexión TCP a `127.0.0.1:3307`, existencia y permisos de sockets UNIX (`mysqld10.sock`), autenticación con MariaDB y existencia de la base de datos `family_feed`.
> - **Corrección Visual en la APK**: Corrección inmediata de la cadena en pantalla para mostrar `root@127.0.0.1:3307` en lugar de `root@:3307`.

---

## 1. Visión General y Diagnóstico del Problema

### El Problema
Al realizar peticiones al servidor Synology Web Station (`index.php`), se obtiene una respuesta **HTTP 500** persistente:
1. **Representación visual confusa en la app**: Se mostraba `MariaDB root@ :3307` debido a la omisión de la variable `dbHost` en la composición del texto en `NasConfigScreen.kt`.
2. **Rechazo de conexión por MariaDB 10**:
   - En Synology DSM, MariaDB 10 tiene por defecto la casilla **"Habilitar conexión TCP/IP"** desmarcada, lo que provoca que los intentos de conexión TCP a `127.0.0.1:3307` sean rechazados con `SQLSTATE[HY000] [2002] Connection refused`.
   - Cuando se intenta conectar mediante socket UNIX, las rutas típicas de MariaDB 10 en DSM (`/run/mysqld/mysqld10.sock` o `/var/run/mysqld/mysqld10.sock`) pueden estar restringidas por la directiva `open_basedir` del perfil PHP 7.3 en Web Station o carecer de permisos para el usuario `http`.
   - Las credenciales configuradas en la interfaz de Web Station no se transmiten automáticamente a las llamadas PDO de PHP si `config.php` mantiene una contraseña distinta a la definida en MariaDB 10 en DSM.
3. **Falta de visibilidad de la causa raíz**: El cliente recibía únicamente un error 500 genérico sin conocer con certeza si el fallo era de red TCP, de archivo de socket, de extensión PHP faltante o de usuario/contraseña denegados (código 1045).

### Beneficios de la Solución
- **Transparencia Total**: Diagnóstico en un solo toque que indica con precisión milimétrica qué componente falla (TCP, Socket, Credenciales o Tablas).
- **Conectividad Resiliente**: `config.php` probará de forma secuencial y transparente todos los métodos válidos de conexión para MariaDB 10 (TCP `127.0.0.1:3307`, TCP `localhost:3307`, socket `/run/mysqld/mysqld10.sock`, socket `/var/run/mysqld/mysqld10.sock` y socket `/run/mysqld/mysqld.sock`).
- **Claridad de Interfaz**: Datos de conexión exactos y visualmente correctos en la APK.

---

## 2. Experiencia de Usuario y Flujo de Diagnóstico

### Flujo de Diagnóstico Paso a Paso
1. **Acceso al Diagnóstico**:
   - El usuario abre la pantalla de **Configuración de Synology NAS** en la app.
   - Si la conexión falla o hay un error 500, aparece un botón destacado: **"Ejecutar Diagnóstico Exhaustivo de MariaDB"**.
2. **Resultados Detallados en Tiempo Real**:
   - La app contacta al endpoint `?action=db_diagnostics` y presenta una tarjeta con indicadores visuales claros (verde/rojo):
     - **Extensión `pdo_mysql`**: `[OK] Cargada en PHP 7.3` / `[ERROR] No habilitada en Web Station`.
     - **Conexión TCP (127.0.0.1:3307)**: `[OK] Puerto abierto` / `[ERROR] Conexión rechazada (¿TCP/IP habilitado en DSM?)`.
     - **Socket UNIX (`mysqld10.sock`)**: `[OK] Socket accesible` / `[AVISO] No encontrado en /run/mysqld/mysqld10.sock`.
     - **Autenticación MariaDB**: `[OK] Conectado como root` / `[ERROR 1045] Contraseña incorrecta en config.php`.
     - **Base de Datos `family_feed`**: `[OK] Existente y accesible` / `[CREADA] Creada automáticamente`.
     - **Tablas de la Aplicación**: `[OK] 8 tablas sincronizadas`.
   - Incluye un botón para **"Copiar Diagnóstico Completo"** para compartirlo o guardarlo.
3. **Exportación de Scripts Actualizados**:
   - La pantalla de configuración permite descargar o copiar los archivos `config.php` e `index.php` actualizados directamente al portapapeles o mediante ZIP para reemplazarlos en `/volume1/web/family_api/`.

---

## 3. Decisiones de Arquitectura y Diseño Técnico

### A. Corrección Visual en la APK Android
- En `app/src/main/java/com/example/ui/screens/NasConfigScreen.kt`:
  - Reemplazar la interpolación errónea:
    - *Antes*: `Text("MariaDB (${currentConfig.dbUser}@:${currentConfig.dbPort})")`
    - *Después*: `Text("MariaDB (${currentConfig.dbUser}@${currentConfig.dbHost}:${currentConfig.dbPort})")`
  - Mostrar explícitamente el socket activo si la conexión se establece mediante socket UNIX en lugar de TCP.

### B. Endpoint de Diagnóstico Exhaustivo en `index.php` (`action=db_diagnostics`)
Se incorporará una acción dedicada que no genera un error 500 fatal si la base de datos no conecta, sino que devuelve código 200 con un informe JSON minucioso:
```json
{
  "status": "DIAGNOSTIC_REPORT",
  "php_version": "7.3.x",
  "checks": {
    "pdo_mysql_extension": { "success": true, "message": "Extensión PDO MySQL disponible" },
    "tcp_port_open": { "success": true, "port": 3307, "host": "127.0.0.1" },
    "unix_socket_exists": { "success": true, "path": "/run/mysqld/mysqld10.sock" },
    "mariadb_authentication": { "success": false, "code": 1045, "error": "Access denied for user 'root'@'127.0.0.1' (using password: YES)" },
    "recommended_action": "La contraseña en config.php no coincide con la configurada en el paquete MariaDB 10 de DSM."
  }
}
```

### C. Robustecimiento de `config.php`
- **Lista de DSN Optimizada para MariaDB 10**:
  1. `mysql:host=127.0.0.1;port=3307;dbname=family_feed;charset=utf8mb4`
  2. `mysql:unix_socket=/run/mysqld/mysqld10.sock;dbname=family_feed;charset=utf8mb4`
  3. `mysql:unix_socket=/var/run/mysqld/mysqld10.sock;dbname=family_feed;charset=utf8mb4`
  4. `mysql:host=localhost;port=3307;dbname=family_feed;charset=utf8mb4`
  5. `mysql:unix_socket=/run/mysqld/mysqld.sock;dbname=family_feed;charset=utf8mb4`
  6. `mysql:host=127.0.0.1;port=3306;dbname=family_feed;charset=utf8mb4`
- **Manejo Específico de Errores PDO**:
  - Distinción entre error de autenticación (`1045 Access denied`: alertar sobre contraseña en `config.php`), error de conexión (`2002 Connection refused`: alertar sobre casilla TCP/IP en MariaDB 10 de DSM) y base de datos inexistente (`1049 Unknown database`: auto-creación con `CREATE DATABASE IF NOT EXISTS`).

### D. Guía Clara de Configuración en Synology DSM para el Usuario
La app y el archivo `README.md` / `config.php` incluirán los tres pasos exactos en DSM:
1. **DSM > MariaDB 10**: Marcar la casilla *"Habilitar conexión TCP/IP"* en el puerto 3307.
2. **DSM > Web Station > Configuración de lenguaje de script > PHP 7.3**: En la pestaña *Extensiones*, asegurarse de marcar `pdo_mysql` y `mysqli`.
3. **Archivo `config.php`**: Editar la línea `define('DB_PASS', 'TuClaveRealDeMariaDB');` con la clave establecida al instalar MariaDB 10.

---

## 4. Diagrama de Arquitectura y Diagnóstico

```
┌────────────────────────────────────────────────────────┐
│                   App Android (M3)                     │
│  - Muestra: MariaDB (root@127.0.0.1:3307)              │
│  - Botón: "Diagnóstico Exhaustivo de MariaDB"          │
│  - Diálogo de Resultados: Tests individuales (OK/FAIL) │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTP GET /index.php?action=db_diagnostics
                           ▼
┌────────────────────────────────────────────────────────┐
│            Synology Web Station (PHP 7.3)              │
│                                                        │
│  Prueba 1: extension_loaded('pdo_mysql')               │
│  Prueba 2: fsockopen('127.0.0.1', 3307, 2s)            │
│  Prueba 3: file_exists('/run/mysqld/mysqld10.sock')    │
│  Prueba 4: Intento PDO con credenciales de config.php   │
│            ├── Código 1045 -> Clave incorrecta en PHP  │
│            ├── Código 2002 -> TCP deshabilitado en DSM │
│            └── Código 1049 -> Auto-crea DB family_feed │
└──────────────────────────┬─────────────────────────────┘
                           │ TCP 3307 o Socket mysqld10.sock
                           ▼
┌────────────────────────────────────────────────────────┐
│                  Synology MariaDB 10                   │
│  - Puerto: 3307                                        │
│  - Sockets: /run/mysqld/mysqld10.sock                  │
│  - Base de Datos: family_feed                          │
└────────────────────────────────────────────────────────┘
```

---

## 5. Fases de Ejecución

- [x] **Fase 1: Corrección de la Interfaz en la APK**
  - Corregida en `NasConfigScreen.kt` la cadena de texto para incluir `currentConfig.dbHost`, mostrando `MariaDB (root@127.0.0.1:3307)`.
  - Añadido botón interactivo de diagnóstico tanto en la tarjeta de estado como en el banner de error de `NasConfigScreen.kt` y en `ErrorLogsDialog.kt`.

- [x] **Fase 2: Implementación del Endpoint de Diagnóstico y Robustecimiento en PHP**
  - Implementada la ruta `action=db_diagnostics` en `synology_server/index.php` y `SynologyScripts.kt` que comprueba extensiones, puerto TCP 3307, sockets UNIX, credenciales y tablas sin retornar nunca 500.
  - Reforzados los mensajes en `synology_server/config.php` y `SynologyScripts.kt` con detección específica de código 1045 (contraseña de MariaDB) y 2002 (TCP/IP en DSM).
  - Añadido fallback `mysql:host=localhost;port=3307;dbname=...` y soporte de variables de entorno / ini.

- [x] **Fase 3: Integración en el Cliente Android (`NasApiClient.kt` & `FamilyRepository.kt`)**
  - Creado `DatabaseDiagnosticDialog.kt` con diseño Material 3, checklist detallado, tarjeta de solución y botón para copiar informe.
  - Agregado el método `getDatabaseDiagnostics()` en `NasApiClient.kt` y expuesto en `FamilyRepository` y `FamilyFeedViewModel`.
  - Integrado el diálogo en `MainActivity.kt`.

- [x] **Fase 4: Verificación y Pruebas Unitarias**
  - Pruebas unitarias ejecutadas exitosamente con Gradle (`BUILD SUCCESSFUL in 25s`).
  - Compilación de la APK exitosa (`compile_applet`).
