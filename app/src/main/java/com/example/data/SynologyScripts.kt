package com.example.data

/**
 * Contiene los scripts completos listos para desplegar en el Synology NAS
 * bajo Web Station (Nginx/Apache + PHP 8.2) y MariaDB 10.
 */
object SynologyScripts {

    val schemaSql: String = """
        -- =========================================================
        -- BASE DE DATOS PARA MURO FAMILIAR LAN EN SYNOLOGY MARIADB 10
        -- =========================================================
        CREATE DATABASE IF NOT EXISTS family_feed 
        CHARACTER SET utf8mb4 
        COLLATE utf8mb4_unicode_ci;

        USE family_feed;

        -- 1. Tabla de Miembros Familiares
        CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            username VARCHAR(50) NOT NULL UNIQUE,
            display_name VARCHAR(100) NOT NULL,
            role VARCHAR(50) NOT NULL,
            pin_hash VARCHAR(255) NOT NULL,
            avatar_emoji VARCHAR(10) DEFAULT '👤',
            avatar_color_hex BIGINT DEFAULT 4280391411,
            bio VARCHAR(255) DEFAULT '',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB;

        -- 2. Tabla de Publicaciones (Muro)
        CREATE TABLE IF NOT EXISTS posts (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            content_text TEXT NULL,
            feeling VARCHAR(100) NULL,
            location VARCHAR(100) NULL,
            is_pinned TINYINT(1) DEFAULT 0,
            is_short TINYINT(1) DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            INDEX idx_created (created_at DESC)
        ) ENGINE=InnoDB;

        -- 3. Tabla de Multimedia (Fotos y Vídeos en el NAS)
        CREATE TABLE IF NOT EXISTS media (
            id INT AUTO_INCREMENT PRIMARY KEY,
            post_id INT NOT NULL,
            media_type ENUM('IMAGE', 'VIDEO') NOT NULL,
            file_path VARCHAR(255) NOT NULL,
            thumb_path VARCHAR(255) NOT NULL,
            duration_label VARCHAR(20) NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
        ) ENGINE=InnoDB;

        -- 4. Tabla de Comentarios
        CREATE TABLE IF NOT EXISTS comments (
            id INT AUTO_INCREMENT PRIMARY KEY,
            post_id INT NOT NULL,
            user_id INT NOT NULL,
            comment_text TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT fk_comm_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
            CONSTRAINT fk_comm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB;

        -- 5. Tabla de Reacciones
        CREATE TABLE IF NOT EXISTS reactions (
            post_id INT NOT NULL,
            user_id INT NOT NULL,
            reaction_type VARCHAR(20) DEFAULT 'LIKE',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (post_id, user_id),
            CONSTRAINT fk_rx_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
            CONSTRAINT fk_rx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB;

        -- 6. Tabla de Historias Independientes (Stories)
        CREATE TABLE IF NOT EXISTS stories (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            caption VARCHAR(255) NULL,
            story_type ENUM('IMAGE', 'TEXT', 'VIDEO') DEFAULT 'IMAGE',
            file_path VARCHAR(255) NULL,
            thumb_path VARCHAR(255) NULL,
            bg_color_hex BIGINT DEFAULT 4280171146,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT fk_story_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            INDEX idx_story_created (created_at DESC)
        ) ENGINE=InnoDB;

        -- Usuario administrador inicial por defecto
        INSERT IGNORE INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES
        (1, 'admin', 'Administrador', 'Administrador', 'admin', '👑', 4280391411, 'Cuenta administradora familiar');
    """.trimIndent()

    private const val D = "$"

    fun generateConfigPhp(
        dbHost: String = "127.0.0.1",
        dbPort: Int = 3307,
        dbName: String = "family_feed",
        dbUser: String = "root",
        dbPass: String = ""
    ): String = """
        <?php
        /**
         * config.php
         * Guardar en: /volume1/web/family_api/config.php
         * Compatible con MariaDB / MySQL en Synology DSM 7 / 6 / Docker
         */
        define('DB_HOST', '$dbHost');
        define('DB_PORT', '$dbPort'); // Puerto configurado para MariaDB / MySQL
        define('DB_NAME', '$dbName');
        define('DB_USER', '$dbUser'); // Usuario MariaDB
        define('DB_PASS', '$dbPass'); // Contraseña MariaDB

        function autoBootstrapDatabase(PDO ${D}pdo) {
            try {
                // 1. REPARAR O CREAR TABLAS QUE NO EXISTAN
                ${D}ddl = "
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) UNIQUE NOT NULL,
                        display_name VARCHAR(100) NOT NULL,
                        role VARCHAR(50) DEFAULT 'Familiar',
                        pin_hash VARCHAR(255) NOT NULL,
                        avatar_emoji VARCHAR(10) DEFAULT '👤',
                        avatar_color_hex BIGINT DEFAULT 4280391411,
                        bio VARCHAR(255) DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS posts (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        content_text TEXT,
                        feeling VARCHAR(100) NULL,
                        location VARCHAR(100) NULL,
                        is_pinned TINYINT(1) DEFAULT 0,
                        is_short TINYINT(1) DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_created (created_at),
                        INDEX idx_is_short (is_short)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS media (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        post_id INT NOT NULL,
                        media_type VARCHAR(20) NOT NULL,
                        file_path VARCHAR(255) NOT NULL,
                        thumb_path VARCHAR(255) NULL,
                        duration_label VARCHAR(20) NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_post (post_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS comments (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        post_id INT NOT NULL,
                        user_id INT NOT NULL,
                        comment_text TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_post_c (post_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS reactions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        post_id INT NOT NULL,
                        user_id INT NOT NULL,
                        reaction_type VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_post_user (post_id, user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS stories (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        caption VARCHAR(255) NULL,
                        story_type VARCHAR(20) DEFAULT 'IMAGE',
                        file_path VARCHAR(255) NULL,
                        thumb_path VARCHAR(255) NULL,
                        bg_color_hex BIGINT DEFAULT 4279892618,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_stories_time (created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                ";
                ${D}pdo->exec(${D}ddl);

                // 2. REPARACIÓN DE COLUMNAS ACTUALIZADAS
                try {
                    ${D}pdo->exec("ALTER TABLE posts ADD COLUMN is_short TINYINT(1) DEFAULT 0");
                } catch (Exception ${D}eCol) {}
                try {
                    ${D}pdo->exec("ALTER TABLE media ADD COLUMN thumb_path VARCHAR(255) NULL");
                } catch (Exception ${D}eCol) {}
                try {
                    ${D}pdo->exec("ALTER TABLE media ADD COLUMN duration_label VARCHAR(20) NULL");
                } catch (Exception ${D}eCol) {}
                try {
                    ${D}pdo->exec("ALTER TABLE stories ADD COLUMN thumb_path VARCHAR(255) NULL");
                } catch (Exception ${D}eCol) {}

                // 3. CREACIÓN EXCLUSIVA DE USUARIO ADMIN SOLO SI LA BD ES NUEVA
                // Si la BD ya existe, no se toca ni recrea el usuario admin, protegiendo credenciales
                ${D}countQuery = ${D}pdo->query("SELECT COUNT(*) FROM users");
                ${D}userCount = ${D}countQuery ? (int)${D}countQuery->fetchColumn() : 0;

                if (${D}userCount === 0) {
                    ${D}hash = password_hash('admin', PASSWORD_DEFAULT);
                    ${D}stmt = ${D}pdo->prepare("INSERT INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES (1, 'admin', 'Administrador', 'Administrador', ?, '👑', 4280391411, 'Cuenta administradora familiar')");
                    ${D}stmt->execute([${D}hash]);
                }
            } catch (Exception ${D}ex) {
                error_log("Bootstrap DB error: " . ${D}ex->getMessage());
            }
        }

        function getDB(): PDO {
            static ${D}pdo = null;
            if (${D}pdo === null) {
                ${D}options = [
                    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES   => false,
                    PDO::ATTR_TIMEOUT            => 5,
                ];

                try {
                    // Conexión regular a la base de datos
                    ${D}dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
                    ${D}pdo = new PDO(${D}dsn, DB_USER, DB_PASS, ${D}options);
                } catch (PDOException ${D}e) {
                    // Si no existe la BD en MariaDB 10, conectarse al servidor y crearla automáticamente
                    if (${D}e->getCode() == 1049 || strpos(${D}e->getMessage(), 'Unknown database') !== false) {
                        ${D}rootDsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";charset=utf8mb4";
                        ${D}serverPdo = new PDO(${D}rootDsn, DB_USER, DB_PASS, ${D}options);
                        ${D}serverPdo->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
                        ${D}pdo = new PDO("mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4", DB_USER, DB_PASS, ${D}options);
                    } else {
                        throw ${D}e;
                    }
                }

                autoBootstrapDatabase(${D}pdo);
            }
            return ${D}pdo;
        }
    """.trimIndent()

    val configPhp: String get() = generateConfigPhp()

    val indexPhp: String = """
        <?php
        /**
         * index.php - API REST para Muro Familiar LAN
         * Compatible con Synology Web Station (PHP 7.0, 7.2, 7.3, 7.4 y PHP 8.x)
         * Guardar en: /volume1/web/family_api/index.php
         * Servir vía Web Station en puerto dedicado (ej: 8085)
         */
        // Prevenir que errores o avisos de PHP impriman HTML (<br />) y rompan el JSON
        ini_set('display_errors', '0');
        error_reporting(E_ALL);

        // Sin límites de tiempo ni tamaño en la ejecución para subida de vídeos pesados
        @set_time_limit(0);
        @ini_set('max_execution_time', '0');
        @ini_set('max_input_time', '0');
        @ini_set('memory_limit', '2048M');
        @ini_set('upload_max_filesize', '4096M');
        @ini_set('post_max_size', '4096M');

        // Polyfills para compatibilidad total con PHP 7 en Synology NAS antiguos
        if (!function_exists('str_ends_with')) {
            function str_ends_with(${D}haystack, ${D}needle) {
                ${D}length = strlen(${D}needle);
                return ${D}length === 0 || (substr(${D}haystack, -${D}length) === ${D}needle);
            }
        }
        if (!function_exists('str_contains')) {
            function str_contains(${D}haystack, ${D}needle) {
                return ${D}needle === '' || strpos(${D}haystack, ${D}needle) !== false;
            }
        }

        header("Access-Control-Allow-Origin: *");
        header("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
        header("Access-Control-Allow-Headers: Content-Type, Authorization");
        header("Content-Type: application/json; charset=UTF-8");

        set_exception_handler(function (${D}e) {
            http_response_code(500);
            echo json_encode(["status" => "ERROR", "error" => ${D}e->getMessage()]);
            exit;
        });

        if (${D}_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
            http_response_code(200);
            exit;
        }

        require_once __DIR__ . '/config.php';

        ${D}action = trim(${D}_GET['action'] ?? ${D}_GET['route'] ?? '', '/');
        ${D}uri = parse_url(${D}_SERVER['REQUEST_URI'], PHP_URL_PATH) ?? '';
        ${D}method = ${D}_SERVER['REQUEST_METHOD'];

        if (!function_exists('matchRoute')) {
            function matchRoute(${D}action, ${D}uri, ${D}target) {
                ${D}t = trim(${D}target, '/');
                if (!empty(${D}action)) {
                    return ${D}action === ${D}t || str_ends_with(${D}action, '/' . ${D}t);
                }
                return str_ends_with(${D}uri, '/' . ${D}t) || str_contains(${D}uri, '/' . ${D}t);
            }
        }

        // 1. Health check / Ping
        if (matchRoute(${D}action, ${D}uri, 'ping') || matchRoute(${D}action, ${D}uri, 'health')) {
            try {
                ${D}db = getDB();
                echo json_encode([
                    "status" => "ONLINE",
                    "server" => "Synology Web Station + MariaDB 10",
                    "time" => date("Y-m-d H:i:s")
                ]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["status" => "ERROR", "message" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 2. OBTENER USUARIOS
        if (${D}method === 'GET' && (matchRoute(${D}action, ${D}uri, 'users') || str_ends_with(${D}uri, '/users'))) {
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->query("SELECT id, username, display_name, role, avatar_emoji, avatar_color_hex, bio, pin_hash FROM users ORDER BY id ASC");
                echo json_encode(${D}stmt->fetchAll());
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 2.1. REGISTRO DE USUARIO FAMILIAR
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'auth/register') || str_ends_with(${D}uri, '/auth/register') || matchRoute(${D}action, ${D}uri, 'register'))) {
            ${D}username = strtolower(trim(${D}_POST['username'] ?? ''));
            ${D}password = trim(${D}_POST['password'] ?? '');
            ${D}displayName = trim(${D}_POST['display_name'] ?? '');
            ${D}role = trim(${D}_POST['role'] ?? 'Familiar');
            ${D}avatarEmoji = trim(${D}_POST['avatar_emoji'] ?? '👤');
            ${D}avatarColorHex = (int)(${D}_POST['avatar_color_hex'] ?? 4280391411);

            if (empty(${D}username) || empty(${D}password) || empty(${D}displayName)) {
                http_response_code(400);
                echo json_encode(["error" => "Completa usuario, contraseña y nombre"]);
                exit;
            }

            try {
                ${D}db = getDB();
                ${D}check = ${D}db->prepare("SELECT id FROM users WHERE username = ?");
                ${D}check->execute([${D}username]);
                if (${D}check->fetch()) {
                    http_response_code(409);
                    echo json_encode(["error" => "El usuario '{${D}username}' ya existe. Elige otro nombre de usuario."]);
                    exit;
                }

                ${D}hash = password_hash(${D}password, PASSWORD_DEFAULT);
                ${D}stmt = ${D}db->prepare("INSERT INTO users (username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex) VALUES (?, ?, ?, ?, ?, ?)");
                ${D}stmt->execute([${D}username, ${D}displayName, ${D}role, ${D}hash, ${D}avatarEmoji, ${D}avatarColorHex]);
                ${D}newId = (int)${D}db->lastInsertId();

                echo json_encode([
                    "status" => "OK",
                    "user" => [
                        "id" => ${D}newId,
                        "username" => ${D}username,
                        "display_name" => ${D}displayName,
                        "role" => ${D}role,
                        "avatar_emoji" => ${D}avatarEmoji,
                        "avatar_color_hex" => ${D}avatarColorHex,
                        "bio" => ""
                    ]
                ]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 2.2. INICIO DE SESIÓN (LOGIN)
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'auth/login') || str_ends_with(${D}uri, '/auth/login') || matchRoute(${D}action, ${D}uri, 'login'))) {
            ${D}username = strtolower(trim(${D}_POST['username'] ?? ''));
            ${D}password = trim(${D}_POST['password'] ?? '');

            if (empty(${D}username) || empty(${D}password)) {
                http_response_code(400);
                echo json_encode(["error" => "Ingresa usuario y contraseña"]);
                exit;
            }

            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->prepare("SELECT id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio FROM users WHERE username = ? OR display_name = ? LIMIT 1");
                ${D}stmt->execute([${D}username, ${D}username]);
                ${D}user = ${D}stmt->fetch();

                if (!${D}user) {
                    http_response_code(401);
                    echo json_encode(["error" => "Usuario '{${D}username}' no encontrado"]);
                    exit;
                }

                ${D}hash = ${D}user['pin_hash'];
                ${D}valid = password_verify(${D}password, ${D}hash) || (${D}password === ${D}hash);

                if (!${D}valid) {
                    http_response_code(401);
                    echo json_encode(["error" => "Contraseña incorrecta"]);
                    exit;
                }

                unset(${D}user['pin_hash']);
                echo json_encode(["status" => "OK", "user" => ${D}user]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 2.3. ACTUALIZAR PERFIL (NICK, AVATAR, PASSWORD)
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'profile/update') || str_ends_with(${D}uri, '/profile/update') || matchRoute(${D}action, ${D}uri, 'profile'))) {
            ${D}userId = (int)(${D}_POST['user_id'] ?? 0);
            ${D}displayName = trim(${D}_POST['display_name'] ?? '');
            ${D}avatarEmoji = trim(${D}_POST['avatar_emoji'] ?? '');
            ${D}avatarColorHex = isset(${D}_POST['avatar_color_hex']) ? (int)${D}_POST['avatar_color_hex'] : null;
            ${D}bio = trim(${D}_POST['bio'] ?? '');
            ${D}newPassword = trim(${D}_POST['new_password'] ?? '');

            if (${D}userId <= 0 || empty(${D}displayName)) {
                http_response_code(400);
                echo json_encode(["error" => "Datos de perfil inválidos"]);
                exit;
            }

            try {
                ${D}db = getDB();
                ${D}sql = "UPDATE users SET display_name = ?, bio = ?";
                ${D}params = [${D}displayName, ${D}bio];

                if (!empty(${D}avatarEmoji)) {
                    ${D}sql .= ", avatar_emoji = ?";
                    ${D}params[] = ${D}avatarEmoji;
                }
                if (${D}avatarColorHex !== null) {
                    ${D}sql .= ", avatar_color_hex = ?";
                    ${D}params[] = ${D}avatarColorHex;
                }
                if (!empty(${D}newPassword)) {
                    ${D}sql .= ", pin_hash = ?";
                    ${D}params[] = password_hash(${D}newPassword, PASSWORD_DEFAULT);
                }

                ${D}sql .= " WHERE id = ?";
                ${D}params[] = ${D}userId;

                ${D}stmt = ${D}db->prepare(${D}sql);
                ${D}stmt->execute(${D}params);

                ${D}stmtUser = ${D}db->prepare("SELECT id, username, display_name, role, avatar_emoji, avatar_color_hex, bio FROM users WHERE id = ?");
                ${D}stmtUser->execute([${D}userId]);
                ${D}updated = ${D}stmtUser->fetch();

                echo json_encode(["status" => "OK", "user" => ${D}updated]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 3. OBTENER FEED COMPLETO (Excluye vídeos subidos solo a Shorts)
        if (${D}method === 'GET' && (matchRoute(${D}action, ${D}uri, 'feed') || matchRoute(${D}action, ${D}uri, 'posts') || str_ends_with(${D}uri, '/feed') || str_ends_with(${D}uri, '/posts'))) {
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->query("
                    SELECT p.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex,
                           m.media_type, m.file_path, m.thumb_path, m.duration_label,
                           (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count,
                           (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) as comments_count
                    FROM posts p
                    JOIN users u ON p.user_id = u.id
                    LEFT JOIN media m ON m.post_id = p.id
                    WHERE (p.is_short = 0 OR p.is_short IS NULL)
                    ORDER BY p.is_pinned DESC, p.created_at DESC
                    LIMIT 50
                ");
                ${D}posts = ${D}stmt->fetchAll();
                echo json_encode(["posts" => ${D}posts]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 3.0. OBTENER SHORTS (VÍDEOS VERTICALES Y MARCADOS COMO SHORTS)
        if (${D}method === 'GET' && (matchRoute(${D}action, ${D}uri, 'shorts') || str_ends_with(${D}uri, '/shorts'))) {
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->query("
                    SELECT p.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex,
                           m.media_type, m.file_path, m.thumb_path, m.duration_label,
                           (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count,
                           (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) as comments_count
                    FROM posts p
                    JOIN users u ON p.user_id = u.id
                    JOIN media m ON m.post_id = p.id
                    WHERE p.is_short = 1
                    ORDER BY p.created_at DESC
                    LIMIT 100
                ");
                echo json_encode(["shorts" => ${D}stmt->fetchAll()]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 3.1. OBTENER COMENTARIOS DE UNA PUBLICACIÓN
        if (${D}method === 'GET' && (matchRoute(${D}action, ${D}uri, 'comments') || str_ends_with(${D}uri, '/comments'))) {
            ${D}postId = (int)(${D}_GET['post_id'] ?? 0);
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->prepare("
                    SELECT c.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex
                    FROM comments c
                    JOIN users u ON c.user_id = u.id
                    WHERE c.post_id = ?
                    ORDER BY c.created_at ASC
                ");
                ${D}stmt->execute([${D}postId]);
                echo json_encode(["comments" => ${D}stmt->fetchAll()]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 3.2. OBTENER HISTORIAS INDEPENDIENTES (STORIES)
        if (${D}method === 'GET' && (matchRoute(${D}action, ${D}uri, 'stories') || str_ends_with(${D}uri, '/stories') || matchRoute(${D}action, ${D}uri, 'story'))) {
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->query("
                    SELECT s.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex
                    FROM stories s
                    JOIN users u ON s.user_id = u.id
                    ORDER BY s.created_at DESC
                    LIMIT 50
                ");
                echo json_encode(["stories" => ${D}stmt->fetchAll()]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 4. CREAR PUBLICACIÓN CON ARCHIVO REAL Y MINIATURA/DURACIÓN (Y SOPORTE IS_SHORT)
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'post') || matchRoute(${D}action, ${D}uri, 'posts') || str_ends_with(${D}uri, '/post') || str_ends_with(${D}uri, '/posts'))) {
            ${D}userId = (int)(${D}_POST['user_id'] ?? 1);
            ${D}text = trim(${D}_POST['content_text'] ?? '');
            ${D}feeling = ${D}_POST['feeling'] ?? null;
            ${D}location = ${D}_POST['location'] ?? null;
            ${D}durationLabel = trim(${D}_POST['duration_label'] ?? '');
            ${D}isShort = (int)(${D}_POST['is_short'] ?? 0);

            try {
                ${D}db = getDB();
                ${D}db->beginTransaction();

                ${D}stmt = ${D}db->prepare("INSERT INTO posts (user_id, content_text, feeling, location, is_short) VALUES (?, ?, ?, ?, ?)");
                ${D}stmt->execute([${D}userId, ${D}text, ${D}feeling, ${D}location, ${D}isShort]);
                ${D}postId = ${D}db->lastInsertId();

                if (isset(${D}_FILES['photo'])) {
                    if (${D}_FILES['photo']['error'] !== UPLOAD_ERR_OK && ${D}_FILES['photo']['error'] !== UPLOAD_ERR_NO_FILE) {
                        ${D}uploadErrors = [
                            1 => 'El archivo supera upload_max_filesize en php.ini del NAS',
                            2 => 'El archivo supera MAX_FILE_SIZE permitido',
                            3 => 'El archivo se subió solo parcialmente',
                            6 => 'Falta la carpeta temporal en el servidor',
                            7 => 'Fallo al escribir el archivo en el disco del NAS',
                            8 => 'Una extensión de PHP detuvo la subida'
                        ];
                        throw new Exception(${D}uploadErrors[${D}_FILES['photo']['error']] ?? ('Error de subida #' . ${D}_FILES['photo']['error']));
                    }
                    if (${D}_FILES['photo']['error'] === UPLOAD_ERR_OK) {
                        ${D}uploadDir = __DIR__ . '/uploads';
                        if (!is_dir(${D}uploadDir . '/originals')) mkdir(${D}uploadDir . '/originals', 0777, true);
                        if (!is_dir(${D}uploadDir . '/thumbs')) mkdir(${D}uploadDir . '/thumbs', 0777, true);

                        ${D}ext = strtolower(pathinfo(${D}_FILES['photo']['name'], PATHINFO_EXTENSION));
                        ${D}uuid = uniqid('media_', true);
                        ${D}relOrig = "uploads/originals/{${D}uuid}.{${D}ext}";
                        ${D}relThumb = "uploads/thumbs/{${D}uuid}.webp";

                        move_uploaded_file(${D}_FILES['photo']['tmp_name'], __DIR__ . '/' . ${D}relOrig);

                        // Si el cliente envió una miniatura extraída explícita (para videos), guardarla
                        if (isset(${D}_FILES['thumbnail']) && ${D}_FILES['thumbnail']['error'] === UPLOAD_ERR_OK) {
                            ${D}thumbFile = "uploads/thumbs/{${D}uuid}_poster.jpg";
                            move_uploaded_file(${D}_FILES['thumbnail']['tmp_name'], __DIR__ . '/' . ${D}thumbFile);
                            ${D}relThumb = ${D}thumbFile;
                        } else if (in_array(${D}ext, ['jpg', 'jpeg', 'png', 'webp'])) {
                            // Generar WebP thumbnail con GD si es imagen
                            createThumbnail(__DIR__ . '/' . ${D}relOrig, __DIR__ . '/' . ${D}relThumb, 720);
                        } else {
                            ${D}relThumb = ${D}relOrig;
                        }

                        ${D}isVideo = in_array(${D}ext, ['mp4', 'mov', 'mkv', 'avi', '3gp', 'webm', 'm4v', 'ts', 'flv']) || isset(${D}_FILES['thumbnail']) || !empty(${D}durationLabel);
                        ${D}stmtMedia = ${D}db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label) VALUES (?, ?, ?, ?, ?)");
                        ${D}stmtMedia->execute([${D}postId, ${D}isVideo ? 'VIDEO' : 'IMAGE', ${D}relOrig, ${D}relThumb, ${D}durationLabel ?: null]);
                    }
                }

                ${D}db->commit();
                echo json_encode(["success" => true, "post_id" => (int)${D}postId]);
            } catch (Exception ${D}e) {
                if (isset(${D}db) && ${D}db->inTransaction()) ${D}db->rollBack();
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 4.1. CREAR HISTORIA INDEPENDIENTE
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'story') || matchRoute(${D}action, ${D}uri, 'stories') || str_ends_with(${D}uri, '/story') || str_ends_with(${D}uri, '/stories'))) {
            ${D}userId = (int)(${D}_POST['user_id'] ?? 1);
            ${D}caption = trim(${D}_POST['caption'] ?? '');
            ${D}storyType = ${D}_POST['story_type'] ?? 'IMAGE';
            ${D}bgColorHex = (int)(${D}_POST['bg_color_hex'] ?? 4280171146);
            ${D}relOrig = null;
            ${D}relThumb = null;

            try {
                ${D}db = getDB();
                if (isset(${D}_FILES['photo']) && ${D}_FILES['photo']['error'] === UPLOAD_ERR_OK) {
                    ${D}uploadDir = __DIR__ . '/uploads';
                    if (!is_dir(${D}uploadDir . '/originals')) mkdir(${D}uploadDir . '/originals', 0777, true);
                    if (!is_dir(${D}uploadDir . '/thumbs')) mkdir(${D}uploadDir . '/thumbs', 0777, true);

                    ${D}ext = strtolower(pathinfo(${D}_FILES['photo']['name'], PATHINFO_EXTENSION));
                    ${D}uuid = uniqid('story_', true);
                    ${D}relOrig = "uploads/originals/{${D}uuid}.{${D}ext}";
                    ${D}relThumb = "uploads/thumbs/{${D}uuid}.webp";

                    move_uploaded_file(${D}_FILES['photo']['tmp_name'], __DIR__ . '/' . ${D}relOrig);
                    if (in_array(${D}ext, ['jpg', 'jpeg', 'png', 'webp'])) {
                        createThumbnail(__DIR__ . '/' . ${D}relOrig, __DIR__ . '/' . ${D}relThumb, 720);
                    } else {
                        ${D}relThumb = ${D}relOrig;
                    }
                }

                ${D}stmt = ${D}db->prepare("INSERT INTO stories (user_id, caption, story_type, file_path, thumb_path, bg_color_hex) VALUES (?, ?, ?, ?, ?, ?)");
                ${D}stmt->execute([${D}userId, ${D}caption, ${D}storyType, ${D}relOrig, ${D}relThumb, ${D}bgColorHex]);
                echo json_encode(["success" => true, "story_id" => (int)${D}db->lastInsertId()]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 5. REACCIÓN REAL
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'reaction') || str_ends_with(${D}uri, '/reaction'))) {
            ${D}input = json_decode(file_get_contents('php://input'), true) ?? ${D}_POST;
            ${D}postId = (int)(${D}input['post_id'] ?? 0);
            ${D}userId = (int)(${D}input['user_id'] ?? 0);
            ${D}rxType = ${D}input['reaction_type'] ?? 'LIKE';

            try {
                ${D}db = getDB();
                ${D}check = ${D}db->prepare("SELECT COUNT(*) FROM reactions WHERE post_id = ? AND user_id = ?");
                ${D}check->execute([${D}postId, ${D}userId]);
                if (${D}check->fetchColumn() > 0) {
                    ${D}del = ${D}db->prepare("DELETE FROM reactions WHERE post_id = ? AND user_id = ?");
                    ${D}del->execute([${D}postId, ${D}userId]);
                    echo json_encode(["action" => "removed"]);
                } else {
                    ${D}ins = ${D}db->prepare("INSERT INTO reactions (post_id, user_id, reaction_type) VALUES (?, ?, ?)");
                    ${D}ins->execute([${D}postId, ${D}userId, ${D}rxType]);
                    echo json_encode(["action" => "added"]);
                }
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 6. COMENTARIO REAL
        if (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'comment') || str_ends_with(${D}uri, '/comment'))) {
            ${D}input = json_decode(file_get_contents('php://input'), true) ?? ${D}_POST;
            ${D}postId = (int)(${D}input['post_id'] ?? 0);
            ${D}userId = (int)(${D}input['user_id'] ?? 0);
            ${D}text = trim(${D}input['comment_text'] ?? '');

            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->prepare("INSERT INTO comments (post_id, user_id, comment_text) VALUES (?, ?, ?)");
                ${D}stmt->execute([${D}postId, ${D}userId, ${D}text]);
                echo json_encode(["success" => true, "comment_id" => (int)${D}db->lastInsertId()]);
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 7. ELIMINAR PUBLICACIÓN REAL
        if ((${D}method === 'DELETE' && (str_contains(${D}uri, '/post') || !empty(${D}_GET['post_id']))) ||
            (${D}method === 'POST' && (matchRoute(${D}action, ${D}uri, 'delete_post') || matchRoute(${D}action, ${D}uri, 'post/delete'))) ||
            (${D}method === 'GET' && matchRoute(${D}action, ${D}uri, 'delete_post'))) {
            ${D}delPostId = (int)(${D}_GET['post_id'] ?? ${D}_POST['post_id'] ?? 0);
            if (${D}delPostId === 0 && preg_match('#/post/(\\d+)#', ${D}uri, ${D}m)) {
                ${D}delPostId = (int)${D}m[1];
            }
            if (${D}delPostId > 0) {
                try {
                    ${D}db = getDB();
                    ${D}mediaStmt = ${D}db->prepare("SELECT file_path, thumb_path FROM media WHERE post_id = ?");
                    ${D}mediaStmt->execute([${D}delPostId]);
                    while (${D}m = ${D}mediaStmt->fetch()) {
                        if (!empty(${D}m['file_path']) && file_exists(__DIR__ . '/' . ${D}m['file_path'])) @unlink(__DIR__ . '/' . ${D}m['file_path']);
                        if (!empty(${D}m['thumb_path']) && file_exists(__DIR__ . '/' . ${D}m['thumb_path'])) @unlink(__DIR__ . '/' . ${D}m['thumb_path']);
                    }
                    ${D}delStmt = ${D}db->prepare("DELETE FROM posts WHERE id = ?");
                    ${D}delStmt->execute([${D}delPostId]);
                    echo json_encode(["success" => true, "deleted_id" => ${D}delPostId]);
                } catch (Exception ${D}e) {
                    http_response_code(500);
                    echo json_encode(["error" => ${D}e->getMessage()]);
                }
                exit;
            }
        }

        function createThumbnail(${D}src, ${D}dest, ${D}maxW = 720) {
            list(${D}w, ${D}h, ${D}type) = @getimagesize(${D}src);
            if (!${D}w || !${D}h) return;
            ${D}ratio = ${D}h / ${D}w;
            ${D}nw = min(${D}w, ${D}maxW);
            ${D}nh = (int)(${D}nw * ${D}ratio);

            ${D}im = null;
            switch (${D}type) {
                case IMAGETYPE_JPEG:
                    ${D}im = @imagecreatefromjpeg(${D}src);
                    break;
                case IMAGETYPE_PNG:
                    ${D}im = @imagecreatefrompng(${D}src);
                    break;
                default:
                    if (defined('IMAGETYPE_WEBP') && ${D}type === IMAGETYPE_WEBP && function_exists('imagecreatefromwebp')) {
                        ${D}im = @imagecreatefromwebp(${D}src);
                    }
                    break;
            }
            if (!${D}im) return;

            ${D}thumb = imagecreatetruecolor(${D}nw, ${D}nh);
            imagecopyresampled(${D}thumb, ${D}im, 0, 0, 0, 0, ${D}nw, ${D}nh, ${D}w, ${D}h);
            if (function_exists('imagewebp')) {
                imagewebp(${D}thumb, ${D}dest, 80);
            } else {
                imagejpeg(${D}thumb, ${D}dest, 80);
            }
            imagedestroy(${D}im);
            imagedestroy(${D}thumb);
        }

        echo json_encode(["message" => "Endpoint no encontrado"]);
    """.trimIndent()
}
