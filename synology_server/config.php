<?php
/**
 * config.php
 * Configuración de conexión MariaDB 10 para Synology DSM 7 / DSM 6
 * Compatible al 100% con PHP 7.3 y MariaDB 10 (soporta TCP/IP 3307 y socket mysqld10.sock)
 * Ruta típica: /volume1/web/family_api/config.php
 */

/**
 * INSTRUCCIONES CLAVE PARA SYNOLOGY DSM 7 / DSM 6 (Cualquier volumen):
 * 1. En DSM 7, MariaDB 10 tiene TCP/IP SIEMPRE ACTIVO en el puerto 3307 por defecto (no tiene casilla de activar/desactivar).
 * 2. Si el sistema o la carpeta web están en volume2 u otro volumen, las rutas y uploads se resuelven de forma 100% automática con __DIR__.
 * 3. En Web Station -> Configuración de lenguaje de script -> PHP 7.3 -> Extensiones: MARCA 'pdo_mysql' y 'mysqli'.
 * 4. Si conectas por socket UNIX, añade ':/run/mysqld' a la directiva open_basedir en Web Station.
 * 5. En la línea DB_PASS abajo, coloca la contraseña que asignaste a MariaDB 10 en DSM.
 */
if (!defined('DB_HOST')) define('DB_HOST', getenv('DB_HOST') ?: '127.0.0.1');
if (!defined('DB_PORT')) define('DB_PORT', getenv('DB_PORT') ?: '3307'); // Puerto por defecto MariaDB 10
if (!defined('DB_NAME')) define('DB_NAME', getenv('DB_NAME') ?: 'family_feed');
if (!defined('DB_USER')) define('DB_USER', getenv('DB_USER') ?: (ini_get('mysqli.default_user') ?: 'root'));
if (!defined('DB_PASS')) define('DB_PASS', getenv('DB_PASS') ?: (ini_get('mysqli.default_pw') ?: 'TuClaveSynology123')); // <--- EDITA ESTA CONTRASEÑA CON TU CLAVE REAL DE MARIADB 10

function autoBootstrapDatabase(PDO $pdo) {
    try {
        // 1. REPARAR O CREAR TABLAS QUE NO EXISTAN (Ejecución individual para máxima compatibilidad con MariaDB 10 / PHP 7.3)
        $tables = [
            "CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL UNIQUE,
                display_name VARCHAR(100) NOT NULL,
                role VARCHAR(50) NOT NULL,
                pin_hash VARCHAR(255) NOT NULL,
                avatar_emoji VARCHAR(10) DEFAULT '👤',
                avatar_color_hex BIGINT DEFAULT 4280391411,
                bio VARCHAR(255) DEFAULT '',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS family_groups (
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS posts (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                group_id INT NULL DEFAULT NULL,
                content_text TEXT NULL,
                feeling VARCHAR(100) NULL,
                location VARCHAR(100) NULL,
                is_pinned TINYINT(1) DEFAULT 0,
                is_short TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_created (created_at DESC),
                INDEX idx_group (group_id),
                INDEX idx_is_short (is_short)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS media (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                media_type VARCHAR(20) NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                thumb_path VARCHAR(255) NOT NULL,
                duration_label VARCHAR(20) NULL,
                is_chunked TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_media_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS media_chunks (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                chunk_index INT NOT NULL,
                total_chunks INT NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                duration_ms INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_chunk_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS comments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                comment_text TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_comm_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS reactions (
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                reaction_type VARCHAR(20) DEFAULT 'LIKE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (post_id, user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS stories (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                caption VARCHAR(255) NULL,
                story_type VARCHAR(20) DEFAULT 'IMAGE',
                file_path VARCHAR(255) NULL,
                thumb_path VARCHAR(255) NULL,
                bg_color_hex BIGINT DEFAULT 4280171146,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_story_created (created_at DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        ];

        foreach ($tables as $tableSql) {
            try {
                $pdo->exec($tableSql);
            } catch (Exception $eTbl) {
                error_log("Error creating table: " . $eTbl->getMessage());
            }
        }

        // 2. MIGRACIONES DE COMPATIBILIDAD Y VISTA PARA LA PALABRA RESERVADA 'groups'
        try {
            $pdo->exec("CREATE OR REPLACE VIEW `groups` AS SELECT * FROM family_groups");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE posts ADD COLUMN is_short TINYINT(1) DEFAULT 0");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE posts ADD COLUMN group_id INT NULL DEFAULT NULL");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE posts ADD INDEX idx_group (group_id)");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE media ADD COLUMN thumb_path VARCHAR(255) NULL");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE media ADD COLUMN duration_label VARCHAR(20) NULL");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE media ADD COLUMN is_chunked TINYINT(1) DEFAULT 0");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("ALTER TABLE stories ADD COLUMN thumb_path VARCHAR(255) NULL");
        } catch (Exception $eCol) {}

        // 3. CREACIÓN EXCLUSIVA DE USUARIO ADMIN SI LA BD ES NUEVA
        try {
            $countQuery = $pdo->query("SELECT COUNT(*) FROM users");
            $userCount = $countQuery ? (int)$countQuery->fetchColumn() : 0;

            if ($userCount === 0) {
                $hash = password_hash('admin', PASSWORD_DEFAULT);
                $stmt = $pdo->prepare("INSERT INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES (1, 'admin', 'Administrador', 'Administrador', ?, '👑', 4280391411, 'Cuenta administradora familiar')");
                $stmt->execute([$hash]);
            }
        } catch (Exception $eUser) {
            error_log("Error seeding default admin: " . $eUser->getMessage());
        }

        // 4. ASEGURAR CARPETAS PARA ARCHIVOS MULTIMEDIA EN EL NAS
        $uploadDir = __DIR__ . '/uploads';
        if (!is_dir($uploadDir . '/originals')) @mkdir($uploadDir . '/originals', 0777, true);
        if (!is_dir($uploadDir . '/thumbs')) @mkdir($uploadDir . '/thumbs', 0777, true);
        if (!is_dir($uploadDir . '/chunks')) @mkdir($uploadDir . '/chunks', 0777, true);
    } catch (Exception $ex) {
        error_log("Bootstrap DB error: " . $ex->getMessage());
    }
}

function getDB(): PDO {
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }

    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
        PDO::ATTR_TIMEOUT            => 5,
    ];
    if (defined('PDO::MYSQL_ATTR_MULTI_STATEMENTS')) {
        $options[PDO::MYSQL_ATTR_MULTI_STATEMENTS] = true;
    }

    // Lista de candidatos de conexión (TCP/IP en 3307 y sockets UNIX típicos de Synology DSM)
    // Lista de candidatos de conexión (TCP/IP en 3307, IP LAN del propio NAS, localhost y sockets en cualquier volumen)
    $dsnList = [
        "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:host=localhost;port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:unix_socket=/run/mysqld/mysqld10.sock;dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:unix_socket=/var/run/mysqld/mysqld10.sock;dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:unix_socket=/run/mysqld/mysqld.sock;dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:unix_socket=/var/run/mysqld/mysqld.sock;dbname=" . DB_NAME . ";charset=utf8mb4",
        "mysql:unix_socket=/tmp/mysql.sock;dbname=" . DB_NAME . ";charset=utf8mb4"
    ];

    if (!empty($_SERVER['SERVER_ADDR'])) {
        $dsnList[] = "mysql:host=" . $_SERVER['SERVER_ADDR'] . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
    }
    $dsnList[] = "mysql:host=[::1];port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";

    // Detectar sockets en cualquier volumen de Synology (/volume1 .. /volume8)
    for ($v = 1; $v <= 8; $v++) {
        $dsnList[] = "mysql:unix_socket=/volume$v/@appdata/MariaDB10/mysqld.sock;dbname=" . DB_NAME . ";charset=utf8mb4";
        $dsnList[] = "mysql:unix_socket=/volume$v/@appstore/MariaDB10/mysqld.sock;dbname=" . DB_NAME . ";charset=utf8mb4";
    }

    $dsnList[] = "mysql:host=" . DB_HOST . ";port=3306;dbname=" . DB_NAME . ";charset=utf8mb4";

    $lastException = null;
    foreach ($dsnList as $dsn) {
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
            break;
        } catch (PDOException $e) {
            $lastException = $e;
            // Si la base de datos aún no existe (código 1049), crearla automáticamente
            if ($e->getCode() == 1049 || strpos($e->getMessage(), 'Unknown database') !== false) {
                try {
                    $rootDsn = preg_replace('/;dbname=[^;]+/', '', $dsn);
                    $serverPdo = new PDO($rootDsn, DB_USER, DB_PASS, $options);
                    $serverPdo->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
                    $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
                    break;
                } catch (Exception $eCreate) {
                    $lastException = $eCreate;
                }
            }
        }
    }

    if ($pdo === null) {
        $lastCode = $lastException ? $lastException->getCode() : 0;
        $lastErr = $lastException ? $lastException->getMessage() : 'Desconocido';
        $openBaseDir = ini_get('open_basedir') ?: 'No configurado';
        if ($lastCode == 1045 || strpos($lastErr, 'Access denied') !== false) {
            $msg = "Error 1045 de autenticación con MariaDB 10: Usuario '" . DB_USER . "' o contraseña incorrectos. Abre 'config.php' en el NAS y actualiza la constante DB_PASS con la contraseña real que asignaste a MariaDB 10 en Synology DSM. Detalle: $lastErr";
        } elseif ($lastCode == 2002 || strpos($lastErr, 'Connection refused') !== false || strpos($lastErr, 'No such file') !== false) {
            $msg = "Error 2002 de conexión con MariaDB 10 (" . DB_HOST . ":" . DB_PORT . " / sockets). En Synology DSM 7, MariaDB 10 tiene TCP/IP siempre activo en el puerto 3307 por defecto. Verifica: 1) Que el paquete 'MariaDB 10' esté 'En ejecución' en el Centro de Paquetes. 2) Si conectas por socket, que la directiva 'open_basedir' en Web Station incluya ':/run/mysqld'. 3) open_basedir actual: $openBaseDir. Detalle: $lastErr";
        } else {
            $msg = "Error al conectar con MariaDB 10 en Synology (" . DB_HOST . ":" . DB_PORT . " ni por sockets UNIX). Verifica que MariaDB 10 esté ejecutándose en DSM y que la contraseña en config.php sea la correcta. open_basedir actual: $openBaseDir. Detalle: $lastErr";
        }
        throw new Exception($msg);
    }

    autoBootstrapDatabase($pdo);
    return $pdo;
}
