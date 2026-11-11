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
    $report = [
        "tables" => [],
        "missing" => [],
        "errors" => []
    ];
    try {
        // 0. COMPROBAR SI LA TABLA 'groups' EXISTE COMO TABLA REAL Y MIGRARLA A 'family_groups'
        try {
            $checkType = $pdo->query("SELECT TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'groups'");
            if ($checkType && $checkType->fetchColumn() === 'BASE TABLE') {
                $hasFamily = false;
                try {
                    $chkFam = $pdo->query("SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_groups'");
                    if ($chkFam && $chkFam->fetchColumn()) $hasFamily = true;
                } catch (Exception $eFam) {}

                if (!$hasFamily) {
                    $pdo->exec("RENAME TABLE `groups` TO `family_groups`");
                } else {
                    $pdo->exec("INSERT IGNORE INTO family_groups (name, folder_path, description, created_at) SELECT name, folder_path, description, created_at FROM `groups`");
                    $pdo->exec("DROP TABLE `groups`");
                }
            }
        } catch (Exception $eGrpMig) {
            error_log("Groups migration note: " . $eGrpMig->getMessage());
        }

        // 1. DDL CANÓNICO PARA LAS 8 TABLAS (Sin claves foráneas en la creación inicial para garantizar que ninguna tabla falle por dependencias)
        $tables = [
            "users" => "CREATE TABLE IF NOT EXISTS users (
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

            "family_groups" => "CREATE TABLE IF NOT EXISTS family_groups (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(150) NOT NULL,
                folder_path VARCHAR(190) NOT NULL,
                description TEXT NULL,
                cover_photo_url VARCHAR(255) NULL,
                created_by_user_id INT DEFAULT 1,
                posts_count INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uq_group_folder (folder_path)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "posts" => "CREATE TABLE IF NOT EXISTS posts (
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

            "media" => "CREATE TABLE IF NOT EXISTS media (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                media_type VARCHAR(20) NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                thumb_path VARCHAR(255) NULL,
                duration_label VARCHAR(20) NULL,
                is_chunked TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_media_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "media_chunks" => "CREATE TABLE IF NOT EXISTS media_chunks (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                chunk_index INT NOT NULL,
                total_chunks INT NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                duration_ms INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_chunk_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "comments" => "CREATE TABLE IF NOT EXISTS comments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                comment_text TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_comm_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "reactions" => "CREATE TABLE IF NOT EXISTS reactions (
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                reaction_type VARCHAR(20) DEFAULT 'LIKE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (post_id, user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "stories" => "CREATE TABLE IF NOT EXISTS stories (
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

        foreach ($tables as $tblName => $tableSql) {
            try {
                $pdo->exec($tableSql);
            } catch (Exception $eTbl) {
                $report["errors"][$tblName] = $eTbl->getMessage();
                error_log("Error creating table {$tblName}: " . $eTbl->getMessage());
            }
        }

        // 2. CREAR VISTA 'groups' PARA RETROCOMPATIBILIDAD CON QUERIES ANTIGUAS
        try {
            $pdo->exec("CREATE OR REPLACE VIEW `groups` AS SELECT * FROM family_groups");
        } catch (Exception $eView) {}

        // 3. MIGRACIONES DE COLUMNAS ACTUALIZADAS
        $colMigrations = [
            "ALTER TABLE posts ADD COLUMN is_short TINYINT(1) DEFAULT 0",
            "ALTER TABLE posts ADD COLUMN group_id INT NULL DEFAULT NULL",
            "ALTER TABLE posts ADD COLUMN feeling VARCHAR(100) NULL",
            "ALTER TABLE posts ADD COLUMN location VARCHAR(100) NULL",
            "ALTER TABLE posts ADD COLUMN is_pinned TINYINT(1) DEFAULT 0",
            "ALTER TABLE posts ADD INDEX idx_group (group_id)",
            "ALTER TABLE posts ADD INDEX idx_is_short (is_short)",
            "ALTER TABLE media ADD COLUMN thumb_path VARCHAR(255) NULL",
            "ALTER TABLE media ADD COLUMN duration_label VARCHAR(20) NULL",
            "ALTER TABLE media ADD COLUMN is_chunked TINYINT(1) DEFAULT 0",
            "ALTER TABLE media_chunks ADD COLUMN duration_ms INT DEFAULT 0",
            "ALTER TABLE stories ADD COLUMN thumb_path VARCHAR(255) NULL",
            "ALTER TABLE stories ADD COLUMN bg_color_hex BIGINT DEFAULT 4280171146",
            "ALTER TABLE family_groups MODIFY folder_path VARCHAR(190) NOT NULL",
            "ALTER TABLE family_groups ADD COLUMN posts_count INT DEFAULT 0",
            "ALTER TABLE family_groups ADD COLUMN cover_photo_url VARCHAR(255) NULL",
            "ALTER TABLE family_groups ADD COLUMN description TEXT NULL"
        ];
        foreach ($colMigrations as $colSql) {
            try { $pdo->exec($colSql); } catch (Exception $eCol) {}
        }

        // 4. ASEGURAR QUE EXISTA AL MENOS UN USUARIO ADMINISTRADOR EN 'users'
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

        // 5. ASEGURAR CARPETAS PARA ARCHIVOS MULTIMEDIA EN EL NAS
        $uploadDir = __DIR__ . '/uploads';
        if (!is_dir($uploadDir . '/originals')) @mkdir($uploadDir . '/originals', 0777, true);
        if (!is_dir($uploadDir . '/thumbs')) @mkdir($uploadDir . '/thumbs', 0777, true);
        if (!is_dir($uploadDir . '/chunks')) @mkdir($uploadDir . '/chunks', 0777, true);
    } catch (Exception $ex) {
        error_log("Bootstrap DB error: " . $ex->getMessage());
        $report["errors"]["general"] = $ex->getMessage();
    }
    return $report;
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
        PDO::ATTR_TIMEOUT            => 4,
    ];
    if (defined('PDO::MYSQL_ATTR_MULTI_STATEMENTS')) {
        $options[PDO::MYSQL_ATTR_MULTI_STATEMENTS] = true;
    }

    // Candidatos de conexión en orden de fiabilidad en Synology DSM 7:
    // 1. TCP 127.0.0.1:3307 (Inmune a restricciones de open_basedir)
    // 2. TCP en la IP local del NAS en 3307
    // 3. Socket /run/mysqld/mysqld10.sock (Requiere :/run/mysqld en open_basedir)
    // 4. Socket /var/run/mysqld/mysqld10.sock
    $targets = [
        ["type" => "tcp", "host" => DB_HOST, "port" => (int)DB_PORT],
    ];
    if (DB_HOST !== '127.0.0.1') {
        $targets[] = ["type" => "tcp", "host" => "127.0.0.1", "port" => (int)DB_PORT];
    }
    if (DB_HOST !== 'localhost') {
        $targets[] = ["type" => "tcp", "host" => "localhost", "port" => (int)DB_PORT];
    }
    if (!empty($_SERVER['SERVER_ADDR']) && $_SERVER['SERVER_ADDR'] !== DB_HOST) {
        $targets[] = ["type" => "tcp", "host" => $_SERVER['SERVER_ADDR'], "port" => (int)DB_PORT];
    }
    $targets[] = ["type" => "socket", "path" => "/run/mysqld/mysqld10.sock"];
    $targets[] = ["type" => "socket", "path" => "/var/run/mysqld/mysqld10.sock"];
    $targets[] = ["type" => "socket", "path" => "/run/mysqld/mysqld.sock"];

    $primaryException = null;
    $allAttemptErrors = [];

    foreach ($targets as $idx => $t) {
        $dsn = ($t["type"] === "tcp")
            ? "mysql:host=" . $t["host"] . ";port=" . $t["port"] . ";charset=utf8mb4"
            : "mysql:unix_socket=" . $t["path"] . ";charset=utf8mb4";

        try {
            // Conectar al servidor MariaDB sin forzar dbname para que jamás falle si la BD aún no ha sido creada
            $tempPdo = new PDO($dsn, DB_USER, DB_PASS, $options);
            // Crear la base de datos si no existe y seleccionarla
            $tempPdo->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            $tempPdo->exec("USE `" . DB_NAME . "`;");
            $pdo = $tempPdo;
            break;
        } catch (PDOException $e) {
            $allAttemptErrors[] = "$dsn -> " . $e->getMessage();
            if ($primaryException === null) {
                // Guardar la excepción del objetivo principal (TCP DB_HOST:DB_PORT)
                $primaryException = $e;
            }
        }
    }

    if ($pdo === null) {
        $errObj = $primaryException ?? new Exception("Sin conexión");
        $code = (int)$errObj->getCode();
        $msg = $errObj->getMessage();
        $openBaseDir = ini_get('open_basedir') ?: 'No configurado';

        // Comprobar error 1045 de credenciales (Access denied)
        $isAuthError = ($code === 1045 || 
            (isset($errObj->errorInfo[1]) && $errObj->errorInfo[1] === 1045) ||
            stripos($msg, 'Access denied') !== false || 
            stripos($msg, 'Acceso denegado') !== false ||
            stripos($msg, 'using password') !== false);

        if ($isAuthError) {
            $errDetail = "Error 1045 de autenticación con MariaDB 10: Usuario '" . DB_USER . "' o contraseña incorrectos en " . DB_HOST . ":" . DB_PORT . ". Abre config.php en el NAS y coloca en DB_PASS la contraseña real de MariaDB 10. Si la olvidaste, cámbiala en DSM -> MariaDB 10. Detalle: $msg";
        } elseif ($code === 2002 || stripos($msg, 'Connection refused') !== false) {
            $errDetail = "Error 2002 de conexión con MariaDB 10 (" . DB_HOST . ":" . DB_PORT . "): Conexión rechazada. Verifica en el Centro de Paquetes de DSM que MariaDB 10 esté 'En ejecución'. Detalle: $msg";
        } elseif (stripos($msg, 'open_basedir') !== false || stripos($msg, 'No such file') !== false) {
            $errDetail = "Error al conectar con MariaDB 10: Los sockets UNIX requieren ':/run/mysqld' en la directiva open_basedir de Web Station -> PHP 7.3. Se recomienda usar TCP (127.0.0.1:3307). Detalle: $msg";
        } else {
            $errDetail = "Error de conexión con MariaDB 10 (" . DB_HOST . ":" . DB_PORT . "): $msg";
        }
        throw new Exception($errDetail);
    }

    autoBootstrapDatabase($pdo);
    return $pdo;
}
