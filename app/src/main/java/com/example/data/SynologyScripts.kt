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

        -- Miembros iniciales reales
        INSERT IGNORE INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES
        (1, 'papa', 'Carlos (Papá)', 'Papá', '1234', '👨‍💼', 4280391411, 'Organizador de los asados de domingo 🍖'),
        (2, 'mama', 'Laura (Mamá)', 'Mamá', '1234', '👩‍🌾', 4293077672, 'Fotógrafa de las plantas y viajes familiares 🌸'),
        (3, 'hija', 'Sofía', 'Hija Mayor', '1234', '👧', 4287518975, 'Estudiante y fan de la música 🎸'),
        (4, 'hijo', 'Mateo', 'Hijo Menor', '1234', '👦', 4283151817, 'Fútbol y ciencia ⚽'),
        (5, 'abuela', 'Rosa (Abuela)', 'Abuela', '1234', '👵', 4294432139, 'Recetas tradicionales de la familia 🥧');
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

        function getDB(): PDO {
            static ${D}pdo = null;
            if (${D}pdo === null) {
                ${D}dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
                ${D}options = [
                    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES   => false,
                    PDO::ATTR_TIMEOUT            => 5,
                ];
                ${D}pdo = new PDO(${D}dsn, DB_USER, DB_PASS, ${D}options);
            }
            return ${D}pdo;
        }
    """.trimIndent()

    val configPhp: String get() = generateConfigPhp()

    val indexPhp: String = """
        <?php
        /**
         * index.php - API REST para Muro Familiar LAN
         * Guardar en: /volume1/web/family_api/index.php
         * Servir vía Web Station en puerto dedicado (ej: 8085)
         */
        // Prevenir que errores o avisos de PHP impriman HTML (<br />) y rompan el JSON
        ini_set('display_errors', '0');
        error_reporting(E_ALL);

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

        ${D}uri = parse_url(${D}_SERVER['REQUEST_URI'], PHP_URL_PATH);
        ${D}method = ${D}_SERVER['REQUEST_METHOD'];

        // 1. Health check / Ping
        if (str_ends_with(${D}uri, '/ping') || str_ends_with(${D}uri, '/health')) {
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
        if (${D}method === 'GET' && str_ends_with(${D}uri, '/users')) {
            try {
                ${D}db = getDB();
                ${D}stmt = ${D}db->query("SELECT id, display_name, role, avatar_emoji, avatar_color_hex, bio, pin_hash FROM users ORDER BY id ASC");
                echo json_encode(${D}stmt->fetchAll());
            } catch (Exception ${D}e) {
                http_response_code(500);
                echo json_encode(["error" => ${D}e->getMessage()]);
            }
            exit;
        }

        // 3. OBTENER FEED COMPLETO
        if (${D}method === 'GET' && (str_ends_with(${D}uri, '/feed') || str_ends_with(${D}uri, '/posts'))) {
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

        // 3.1. OBTENER COMENTARIOS DE UNA PUBLICACIÓN
        if (${D}method === 'GET' && (str_ends_with(${D}uri, '/comments') || str_contains(${D}uri, '/comments'))) {
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
        if (${D}method === 'GET' && (str_ends_with(${D}uri, '/stories') || str_ends_with(${D}uri, '/story'))) {
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

        // 4. CREAR PUBLICACIÓN CON ARCHIVO REAL Y MINIATURA/DURACIÓN
        if (${D}method === 'POST' && (str_ends_with(${D}uri, '/post') || str_ends_with(${D}uri, '/posts'))) {
            ${D}userId = (int)(${D}_POST['user_id'] ?? 1);
            ${D}text = trim(${D}_POST['content_text'] ?? '');
            ${D}feeling = ${D}_POST['feeling'] ?? null;
            ${D}location = ${D}_POST['location'] ?? null;
            ${D}durationLabel = trim(${D}_POST['duration_label'] ?? '');

            try {
                ${D}db = getDB();
                ${D}db->beginTransaction();

                ${D}stmt = ${D}db->prepare("INSERT INTO posts (user_id, content_text, feeling, location) VALUES (?, ?, ?, ?)");
                ${D}stmt->execute([${D}userId, ${D}text, ${D}feeling, ${D}location]);
                ${D}postId = ${D}db->lastInsertId();

                if (isset(${D}_FILES['photo']) && ${D}_FILES['photo']['error'] === UPLOAD_ERR_OK) {
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

                    ${D}isVideo = in_array(${D}ext, ['mp4', 'mov', 'mkv', 'avi']);
                    ${D}stmtMedia = ${D}db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label) VALUES (?, ?, ?, ?, ?)");
                    ${D}stmtMedia->execute([${D}postId, ${D}isVideo ? 'VIDEO' : 'IMAGE', ${D}relOrig, ${D}relThumb, ${D}durationLabel ?: null]);
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
        if (${D}method === 'POST' && (str_ends_with(${D}uri, '/story') || str_ends_with(${D}uri, '/stories'))) {
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
        if (${D}method === 'POST' && str_ends_with(${D}uri, '/reaction')) {
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
        if (${D}method === 'POST' && str_ends_with(${D}uri, '/comment')) {
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

        function createThumbnail(${D}src, ${D}dest, ${D}maxW = 720) {
            list(${D}w, ${D}h, ${D}type) = @getimagesize(${D}src);
            if (!${D}w) return;
            ${D}ratio = ${D}h / ${D}w;
            ${D}nw = min(${D}w, ${D}maxW);
            ${D}nh = (int)(${D}nw * ${D}ratio);

            ${D}im = match(${D}type) {
                IMAGETYPE_JPEG => @imagecreatefromjpeg(${D}src),
                IMAGETYPE_PNG  => @imagecreatefrompng(${D}src),
                IMAGETYPE_WEBP => @imagecreatefromwebp(${D}src),
                default        => null
            };
            if (!${D}im) return;

            ${D}thumb = imagecreatetruecolor(${D}nw, ${D}nh);
            imagecopyresampled(${D}thumb, ${D}im, 0, 0, 0, 0, ${D}nw, ${D}nh, ${D}w, ${D}h);
            imagewebp(${D}thumb, ${D}dest, 80);
            imagedestroy(${D}im);
            imagedestroy(${D}thumb);
        }

        echo json_encode(["message" => "Endpoint no encontrado"]);
    """.trimIndent()
}
