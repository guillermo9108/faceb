<?php
/**
 * config.php
 * Configuración de conexión MariaDB 10 para Synology DSM 7 / DSM 6
 * Ruta típica: /volume1/web/family_api/config.php
 */

define('DB_HOST', '127.0.0.1');
define('DB_PORT', '3307'); // Puerto por defecto de MariaDB 10 en Synology
define('DB_NAME', 'family_feed');
define('DB_USER', 'root'); // O usuario creado en phpMyAdmin
define('DB_PASS', 'TuClaveSynology123'); // Cambia por tu contraseña de MariaDB

function autoBootstrapDatabase(PDO $pdo) {
    try {
        // 1. REPARAR O CREAR TABLAS QUE NO EXISTAN (Ejecución individual para máxima compatibilidad con MariaDB / MySQL)
        $tables = [
            "CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                role VARCHAR(50) DEFAULT 'Familiar',
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
                content_text TEXT,
                feeling VARCHAR(100) NULL,
                location VARCHAR(100) NULL,
                is_pinned TINYINT(1) DEFAULT 0,
                is_short TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_created (created_at),
                INDEX idx_is_short (is_short),
                INDEX idx_group (group_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS media (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                media_type VARCHAR(20) NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                thumb_path VARCHAR(255) NULL,
                duration_label VARCHAR(20) NULL,
                is_chunked TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS media_chunks (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                chunk_index INT NOT NULL,
                total_chunks INT NOT NULL,
                file_path VARCHAR(255) NOT NULL,
                duration_ms INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_chunks_post (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS comments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                comment_text TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_post_c (post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS reactions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                post_id INT NOT NULL,
                user_id INT NOT NULL,
                reaction_type VARCHAR(20) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uq_post_user (post_id, user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE TABLE IF NOT EXISTS stories (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                caption VARCHAR(255) NULL,
                story_type VARCHAR(20) DEFAULT 'IMAGE',
                file_path VARCHAR(255) NULL,
                thumb_path VARCHAR(255) NULL,
                bg_color_hex BIGINT DEFAULT 4279892618,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_stories_time (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        ];

        foreach ($tables as $tableSql) {
            try {
                $pdo->exec($tableSql);
            } catch (Exception $eTbl) {
                error_log("Error creating table: " . $eTbl->getMessage());
            }
        }

        // 2. REPARACIÓN DE COLUMNAS ACTUALIZADAS Y CLAVES FORÁNEAS
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
            $pdo->exec("ALTER TABLE posts ADD CONSTRAINT fk_post_group FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE SET NULL");
        } catch (Exception $eCol) {}
        try {
            $pdo->exec("CREATE OR REPLACE VIEW `groups` AS SELECT * FROM family_groups");
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

        // 3. CREACIÓN EXCLUSIVA DE USUARIO ADMIN SOLO SI LA BD ES NUEVA
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
    if ($pdo === null) {
        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
            PDO::ATTR_TIMEOUT            => 5,
            PDO::MYSQL_ATTR_MULTI_STATEMENTS => true,
        ];

        try {
            // Conexión regular a la base de datos
            $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            // Si no existe la BD en MariaDB 10, conectarse al servidor y crearla automáticamente
            if ($e->getCode() == 1049 || strpos($e->getMessage(), 'Unknown database') !== false) {
                $rootDsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";charset=utf8mb4";
                $serverPdo = new PDO($rootDsn, DB_USER, DB_PASS, $options);
                $serverPdo->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
                $pdo = new PDO("mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4", DB_USER, DB_PASS, $options);
            } else {
                throw $e;
            }
        }

        autoBootstrapDatabase($pdo);
    }
    return $pdo;
}
