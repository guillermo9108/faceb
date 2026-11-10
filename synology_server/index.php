<?php
/**
 * index.php - API REST para Muro Familiar LAN
 * Totalmente compatible con PHP 7.3 y MariaDB 10 en Synology DSM 6 y DSM 7
 * Servir vía Web Station en puerto dedicado (ej: 8085 o puerto 80)
 * Ruta en NAS: /volume1/web/family_api/index.php
 */

ini_set('display_errors', '0');
error_reporting(E_ALL);

@set_time_limit(180);
@ini_set('max_execution_time', '180');
@ini_set('max_input_time', '180');
@ini_set('memory_limit', '1024M');
@ini_set('upload_max_filesize', '2048M');
@ini_set('post_max_size', '2048M');

// Polyfills para compatibilidad estricta con PHP 7.3
if (!function_exists('str_starts_with')) {
    function str_starts_with($haystack, $needle) {
        return (string)$needle !== '' && strncmp($haystack, $needle, strlen($needle)) === 0;
    }
}
if (!function_exists('str_ends_with')) {
    function str_ends_with($haystack, $needle) {
        $length = strlen($needle);
        return $length === 0 || (substr($haystack, -$length) === $needle);
    }
}
if (!function_exists('str_contains')) {
    function str_contains($haystack, $needle) {
        return $needle === '' || strpos($haystack, $needle) !== false;
    }
}

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, Range");
header("Access-Control-Expose-Headers: Content-Range, Accept-Ranges, Content-Length");

set_exception_handler(function ($e) {
    http_response_code(500);
    header("Content-Type: application/json; charset=UTF-8");
    echo json_encode([
        "status" => "ERROR",
        "error" => $e->getMessage(),
        "file" => basename($e->getFile()),
        "line" => $e->getLine(),
        "php_version" => PHP_VERSION
    ]);
    exit;
});

register_shutdown_function(function() {
    $err = error_get_last();
    if ($err && in_array($err['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        if (!headers_sent()) {
            http_response_code(500);
            header("Content-Type: application/json; charset=UTF-8");
        }
        echo json_encode([
            "status" => "FATAL_ERROR",
            "error" => $err['message'],
            "file" => basename($err['file']),
            "line" => $err['line'],
            "php_version" => PHP_VERSION
        ]);
    }
});

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once __DIR__ . '/config.php';

$action = trim($_GET['action'] ?? $_GET['route'] ?? '', '/');
$uri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?? '';
$method = $_SERVER['REQUEST_METHOD'];

function matchRoute($action, $uri, $target) {
    $t = trim($target, '/');
    if (!empty($action)) {
        return $action === $t;
    }
    $pattern = '#/(?:index\\.php/)?' . preg_quote($t, '#') . '(?:$|\\?)#';
    return preg_match($pattern, $uri) === 1;
}

function streamMediaFile($filePath) {
    if (!@file_exists($filePath) || !@is_readable($filePath)) {
        http_response_code(404);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "Archivo multimedia no encontrado en el NAS: " . basename($filePath)]);
        exit;
    }

    $fileSize = filesize($filePath);
    $ext = strtolower(pathinfo($filePath, PATHINFO_EXTENSION));
    $mimeTypes = [
        'mp4' => 'video/mp4', 'm4v' => 'video/mp4', 'mov' => 'video/quicktime',
        'mkv' => 'video/x-matroska', 'webm' => 'video/webm', 'avi' => 'video/x-msvideo',
        'jpg' => 'image/jpeg', 'jpeg' => 'image/jpeg', 'png' => 'image/png',
        'webp' => 'image/webp', 'gif' => 'image/gif'
    ];
    $contentType = $mimeTypes[$ext] ?? 'application/octet-stream';

    header("Content-Type: $contentType");
    header("Accept-Ranges: bytes");
    header("Cache-Control: public, max-age=86400");

    $start = 0;
    $end = $fileSize - 1;

    if (isset($_SERVER['HTTP_RANGE'])) {
        $range = $_SERVER['HTTP_RANGE'];
        if (preg_match('/bytes=(\d+)-(\d*)/i', $range, $matches)) {
            $start = (int)$matches[1];
            if (!empty($matches[2])) {
                $end = (int)$matches[2];
            }
        }
        if ($start > $end || $start >= $fileSize) {
            http_response_code(416);
            header("Content-Range: bytes */$fileSize");
            exit;
        }
        http_response_code(206);
        $length = $end - $start + 1;
        header("Content-Length: $length");
        header("Content-Range: bytes $start-$end/$fileSize");
    } else {
        header("Content-Length: $fileSize");
    }

    $fp = @fopen($filePath, 'rb');
    if ($fp) {
        if ($start > 0) {
            fseek($fp, $start);
        }
        $buffer = 256 * 1024;
        $bytesLeft = $end - $start + 1;
        while (!feof($fp) && $bytesLeft > 0 && connection_status() == 0) {
            $readSize = min($buffer, $bytesLeft);
            $data = fread($fp, $readSize);
            echo $data;
            flush();
            $bytesLeft -= strlen($data);
        }
        fclose($fp);
    }
    exit;
}

// 0. PING
if (matchRoute($action, $uri, 'ping')) {
    header("Content-Type: application/json; charset=UTF-8");
    echo json_encode([
        "status" => "ONLINE",
        "app" => "Muro Familiar LAN",
        "server" => "Synology Web Station + MariaDB 10",
        "php_version" => PHP_VERSION,
        "database" => DB_NAME,
        "time" => date("Y-m-d H:i:s")
    ]);
    exit;
}

// 1. DIAGNÓSTICO EN VIVO
if (matchRoute($action, $uri, 'db_diagnostics') || matchRoute($action, $uri, 'diagnostic')) {
    header("Content-Type: application/json; charset=UTF-8");
    $currentDir = __DIR__;
    $detectedVol = 'volume1';
    if (preg_match('#/(volume\d+)/#', $currentDir, $m)) {
        $detectedVol = $m[1];
    }

    $report = [
        "status" => "DIAGNOSTIC_REPORT",
        "timestamp" => date("Y-m-d H:i:s"),
        "php_version" => PHP_VERSION,
        "current_dir" => $currentDir,
        "detected_volume" => $detectedVol,
        "config" => [
            "db_host" => DB_HOST,
            "db_port" => (int)DB_PORT,
            "db_name" => DB_NAME,
            "db_user" => DB_USER,
            "db_pass_is_default" => (DB_PASS === 'TuClaveSynology123' || empty(DB_PASS))
        ],
        "checks" => [
            "php_extensions" => [
                "pdo" => extension_loaded('pdo'),
                "pdo_mysql" => extension_loaded('pdo_mysql'),
                "mysqli" => extension_loaded('mysqli'),
                "gd" => extension_loaded('gd'),
                "success" => (extension_loaded('pdo') && extension_loaded('pdo_mysql')),
                "message" => (extension_loaded('pdo') && extension_loaded('pdo_mysql')) 
                    ? "Extensiones PDO y pdo_mysql activas en PHP " . PHP_VERSION 
                    : "FALTA la extensión pdo_mysql en el perfil PHP de Web Station"
            ]
        ]
    ];

    $tcpSuccess = false;
    $tcpErr = "";
    $fp = @fsockopen(DB_HOST, (int)DB_PORT, $errno, $errstr, 2);
    if ($fp) {
        $tcpSuccess = true;
        fclose($fp);
    } else {
        $tcpErr = "No responde el puerto " . DB_PORT . " en " . DB_HOST . " ($errstr)";
    }
    $report["checks"]["tcp_connection"] = [
        "success" => $tcpSuccess,
        "host" => DB_HOST,
        "port" => (int)DB_PORT,
        "error" => $tcpErr,
        "message" => $tcpSuccess 
            ? "Puerto TCP " . DB_PORT . " respondiendo correctamente en MariaDB 10." 
            : $tcpErr
    ];

    $authSuccess = false;
    $authError = "";
    $serverVer = "Desconocido";
    $tables = [];
    $bootstrapReport = null;

    try {
        $diagPdo = getDB();
        $authSuccess = true;
        $serverVer = $diagPdo->getAttribute(PDO::ATTR_SERVER_VERSION);
        $bootstrapReport = autoBootstrapDatabase($diagPdo);

        $st = $diagPdo->query("SHOW TABLES");
        if ($st) {
            $tables = $st->fetchAll(PDO::FETCH_COLUMN);
        }
    } catch (Exception $ex) {
        $authError = $ex->getMessage();
    }

    $expectedTables = ['users', 'family_groups', 'posts', 'media', 'media_chunks', 'comments', 'reactions', 'stories'];
    $effectiveTables = $tables;
    if (in_array('groups', $tables) && !in_array('family_groups', $tables)) {
        $effectiveTables[] = 'family_groups';
    }
    $missingTables = array_values(array_diff($expectedTables, $effectiveTables));

    $report["checks"]["mariadb_authentication"] = [
        "success" => $authSuccess,
        "server_version" => $serverVer,
        "database_exists" => $authSuccess,
        "tables_count" => count($tables),
        "expected_tables_count" => 8,
        "tables" => $tables,
        "missing_tables" => $missingTables,
        "error" => $authError,
        "message" => $authSuccess 
            ? "Conexión exitosa con MariaDB ($serverVer). Tablas encontradas: " . count($tables) . "/8 (" . implode(', ', $tables) . ")"
            : "Fallo de conexión o autenticación: $authError"
    ];

    if ($authSuccess && count($missingTables) === 0) {
        $report["recommendation"] = "¡Conexión perfecta! MariaDB 10 y Web Station en $detectedVol están 100% sincronizados. Las 8 tablas están operativas.";
    } elseif ($authSuccess) {
        $report["recommendation"] = "Faltan tablas: " . implode(', ', $missingTables) . ". Pulsa 'Verificar / Reparar' en la app.";
    } else {
        $report["recommendation"] = "Error de conexión: $authError. Revisa config.php.";
    }

    echo json_encode($report);
    exit;
}

// 1.1. REPARACIÓN AUTOMÁTICA
if (matchRoute($action, $uri, 'repair_db') || matchRoute($action, $uri, 'repair')) {
    try {
        $db = getDB();
        $bReport = autoBootstrapDatabase($db);

        $tablesStmt = $db->query("SHOW TABLES");
        $tables = $tablesStmt ? $tablesStmt->fetchAll(PDO::FETCH_COLUMN) : [];
        $expected = ['users', 'family_groups', 'posts', 'media', 'media_chunks', 'comments', 'reactions', 'stories'];
        $effective = $tables;
        if (in_array('groups', $tables) && !in_array('family_groups', $tables)) {
            $effective[] = 'family_groups';
        }
        $missing = array_values(array_diff($expected, $effective));

        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => count($missing) === 0 ? "OK" : "PARTIAL",
            "message" => count($missing) === 0 
                ? "Todas las 8 tablas en MariaDB 10 verificadas y sincronizadas exitosamente." 
                : "Atención: Faltan " . count($missing) . " tabla(s): " . implode(', ', $missing),
            "database" => DB_NAME,
            "tables" => $tables,
            "expected_count" => 8,
            "missing_tables" => $missing,
            "errors" => $bReport["errors"] ?? []
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["status" => "ERROR", "error" => $e->getMessage()]);
    }
    exit;
}

// 1.2. STREAMING / PROXY
if ($method === 'GET' && (matchRoute($action, $uri, 'media_proxy') || matchRoute($action, $uri, 'stream'))) {
    $filePath = $_GET['path'] ?? '';
    if (!empty($filePath) && !str_starts_with($filePath, '/')) {
        $filePath = __DIR__ . '/' . ltrim($filePath, '/');
    }
    streamMediaFile($filePath);
    exit;
}

// 2. USUARIOS
if ($method === 'GET' && matchRoute($action, $uri, 'users')) {
    try {
        $db = getDB();
        $stmt = $db->query("SELECT id, username, display_name, role, avatar_emoji, avatar_color_hex, bio FROM users ORDER BY id ASC");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode($stmt->fetchAll());
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 2.1. LOGIN
if ($method === 'POST' && matchRoute($action, $uri, 'auth/login')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $username = trim($input['username'] ?? '');
    $pin = trim($input['pin'] ?? '');

    try {
        $db = getDB();
        $stmt = $db->prepare("SELECT * FROM users WHERE username = ? LIMIT 1");
        $stmt->execute([$username]);
        $user = $stmt->fetch();

        if ($user && (password_verify($pin, $user['pin_hash']) || $user['pin_hash'] === $pin)) {
            unset($user['pin_hash']);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["status" => "OK", "user" => $user]);
        } else {
            http_response_code(401);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["error" => "Nombre de usuario o PIN incorrecto"]);
        }
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 2.2. REGISTRO
if ($method === 'POST' && matchRoute($action, $uri, 'auth/register')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $username = trim($input['username'] ?? '');
    $pin = trim($input['pin'] ?? '');
    $displayName = trim($input['display_name'] ?? $username);
    $role = trim($input['role'] ?? 'Familiar');
    $avatarEmoji = $input['avatar_emoji'] ?? '👤';
    $avatarColor = (int)($input['avatar_color_hex'] ?? 4280391411);

    if (empty($username) || empty($pin)) {
        http_response_code(400);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "El usuario y el PIN son obligatorios"]);
        exit;
    }

    try {
        $db = getDB();
        $hash = password_hash($pin, PASSWORD_DEFAULT);
        $stmt = $db->prepare("INSERT INTO users (username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex) VALUES (?, ?, ?, ?, ?, ?)");
        $stmt->execute([$username, $displayName, $role, $hash, $avatarEmoji, $avatarColor]);
        $newId = (int)$db->lastInsertId();

        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => "OK",
            "user" => [
                "id" => $newId, "username" => $username, "display_name" => $displayName,
                "role" => $role, "avatar_emoji" => $avatarEmoji, "avatar_color_hex" => $avatarColor
            ]
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 2.3. ACTUALIZAR PERFIL
if ($method === 'POST' && matchRoute($action, $uri, 'profile/update')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $userId = (int)($input['user_id'] ?? 0);
    $displayName = trim($input['display_name'] ?? '');
    $avatarEmoji = $input['avatar_emoji'] ?? '';
    $avatarColorHex = isset($input['avatar_color_hex']) ? (int)$input['avatar_color_hex'] : null;
    $newPassword = trim($input['new_password'] ?? '');

    try {
        $db = getDB();
        $sql = "UPDATE users SET updated_at = NOW()";
        $params = [];
        if (!empty($displayName)) { $sql .= ", display_name = ?"; $params[] = $displayName; }
        if (!empty($avatarEmoji)) { $sql .= ", avatar_emoji = ?"; $params[] = $avatarEmoji; }
        if ($avatarColorHex !== null) { $sql .= ", avatar_color_hex = ?"; $params[] = $avatarColorHex; }
        if (!empty($newPassword)) { $sql .= ", pin_hash = ?"; $params[] = password_hash($newPassword, PASSWORD_DEFAULT); }
        $sql .= " WHERE id = ?"; $params[] = $userId;

        $db->prepare($sql)->execute($params);
        $uStmt = $db->prepare("SELECT id, username, display_name, role, avatar_emoji, avatar_color_hex, bio FROM users WHERE id = ?");
        $uStmt->execute([$userId]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["status" => "OK", "user" => $uStmt->fetch()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3. GRUPOS
if ($method === 'GET' && matchRoute($action, $uri, 'groups')) {
    try {
        $db = getDB();
        $stmt = $db->query("SELECT g.*, (SELECT COUNT(*) FROM posts p WHERE p.group_id = g.id) as posts_count FROM family_groups g ORDER BY g.updated_at DESC");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["groups" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3.1. PUBLICACIONES DE UN GRUPO
if ($method === 'GET' && matchRoute($action, $uri, 'group/posts')) {
    $groupId = (int)($_GET['group_id'] ?? 0);
    try {
        $db = getDB();
        $stmt = $db->prepare("
            SELECT p.*, 
                   COALESCE(u.display_name, 'Familiar') as display_name,
                   COALESCE(u.role, 'Familiar') as role,
                   COALESCE(u.avatar_emoji, '👤') as avatar_emoji,
                   COALESCE(u.avatar_color_hex, 4280391411) as avatar_color_hex,
                   g.name as group_name,
                   m.media_type, m.file_path, m.thumb_path, m.duration_label, m.is_chunked,
                   (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) as comments_count
            FROM posts p
            LEFT JOIN users u ON p.user_id = u.id
            JOIN family_groups g ON g.id = p.group_id
            LEFT JOIN media m ON m.post_id = p.id
            WHERE p.group_id = ?
            ORDER BY p.is_pinned DESC, p.created_at DESC
            LIMIT 100
        ");
        $stmt->execute([$groupId]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["posts" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3.2. SHORTS
if ($method === 'GET' && matchRoute($action, $uri, 'shorts')) {
    try {
        $db = getDB();
        $stmt = $db->query("
            SELECT p.*, 
                   COALESCE(u.display_name, 'Familiar') as display_name,
                   COALESCE(u.role, 'Familiar') as role,
                   COALESCE(u.avatar_emoji, '👤') as avatar_emoji,
                   COALESCE(u.avatar_color_hex, 4280391411) as avatar_color_hex,
                   g.name as group_name,
                   m.media_type, m.file_path, m.thumb_path, m.duration_label, m.is_chunked,
                   (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) as comments_count
            FROM posts p
            LEFT JOIN users u ON p.user_id = u.id
            LEFT JOIN family_groups g ON g.id = p.group_id
            LEFT JOIN media m ON m.post_id = p.id
            WHERE p.is_short = 1
            ORDER BY p.created_at DESC
            LIMIT 50
        ");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["shorts" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3.3. FEED
if ($method === 'GET' && (matchRoute($action, $uri, 'feed') || matchRoute($action, $uri, 'posts'))) {
    try {
        $db = getDB();
        $stmt = $db->query("
            SELECT p.*, 
                   COALESCE(u.display_name, 'Familiar') as display_name,
                   COALESCE(u.role, 'Familiar') as role,
                   COALESCE(u.avatar_emoji, '👤') as avatar_emoji,
                   COALESCE(u.avatar_color_hex, 4280391411) as avatar_color_hex,
                   g.name as group_name,
                   m.media_type, m.file_path, m.thumb_path, m.duration_label, m.is_chunked,
                   (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) as comments_count
            FROM posts p
            LEFT JOIN users u ON p.user_id = u.id
            LEFT JOIN family_groups g ON g.id = p.group_id
            LEFT JOIN media m ON m.post_id = p.id
            ORDER BY p.is_pinned DESC, p.created_at DESC
            LIMIT 50
        ");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["posts" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 4. CREAR PUBLICACIÓN CON ARCHIVO DIRECTO
if ($method === 'POST' && (matchRoute($action, $uri, 'post') || matchRoute($action, $uri, 'posts'))) {
    $userId = (int)($_POST['user_id'] ?? 1);
    $text = trim($_POST['content_text'] ?? '');
    $feeling = $_POST['feeling'] ?? null;
    $location = $_POST['location'] ?? null;
    $durationLabel = trim($_POST['duration_label'] ?? '');
    $isShort = (int)($_POST['is_short'] ?? 0);
    $groupId = !empty($_POST['group_id']) ? (int)$_POST['group_id'] : null;

    try {
        $db = getDB();

        // Validar que userId existe
        $checkUser = $db->prepare("SELECT id FROM users WHERE id = ? LIMIT 1");
        $checkUser->execute([$userId]);
        if (!$checkUser->fetchColumn()) {
            $uFirst = $db->query("SELECT id FROM users ORDER BY id ASC LIMIT 1")->fetchColumn();
            $userId = $uFirst ? (int)$uFirst : 1;
        }

        if ($groupId) {
            $chkG = $db->prepare("SELECT id FROM family_groups WHERE id = ? LIMIT 1");
            $chkG->execute([$groupId]);
            if (!$chkG->fetchColumn()) $groupId = null;
        }

        $db->beginTransaction();

        $stmt = $db->prepare("INSERT INTO posts (user_id, group_id, content_text, feeling, location, is_short) VALUES (?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $groupId, $text, $feeling, $location, $isShort]);
        $postId = (int)$db->lastInsertId();

        $uploadDir = __DIR__ . '/uploads';
        if (!is_dir($uploadDir . '/originals')) @mkdir($uploadDir . '/originals', 0777, true);
        if (!is_dir($uploadDir . '/thumbs')) @mkdir($uploadDir . '/thumbs', 0777, true);

        // Recolectar todos los archivos subidos (individuales, en array 'photos[]' o con prefijo 'photo_')
        $uploadedFiles = [];
        if (isset($_FILES['photo']) && is_array($_FILES['photo']['name'])) {
            for ($fi = 0; $fi < count($_FILES['photo']['name']); $fi++) {
                if ($_FILES['photo']['error'][$fi] === UPLOAD_ERR_OK) {
                    $uploadedFiles[] = [
                        'name' => $_FILES['photo']['name'][$fi],
                        'tmp_name' => $_FILES['photo']['tmp_name'][$fi]
                    ];
                }
            }
        } else if (isset($_FILES['photo']) && $_FILES['photo']['error'] === UPLOAD_ERR_OK) {
            $uploadedFiles[] = [
                'name' => $_FILES['photo']['name'],
                'tmp_name' => $_FILES['photo']['tmp_name']
            ];
        }

        if (isset($_FILES['photos']) && is_array($_FILES['photos']['name'])) {
            for ($fi = 0; $fi < count($_FILES['photos']['name']); $fi++) {
                if ($_FILES['photos']['error'][$fi] === UPLOAD_ERR_OK) {
                    $uploadedFiles[] = [
                        'name' => $_FILES['photos']['name'][$fi],
                        'tmp_name' => $_FILES['photos']['tmp_name'][$fi]
                    ];
                }
            }
        }

        foreach ($_FILES as $fk => $fv) {
            if (strpos($fk, 'photo_') === 0 || strpos($fk, 'media_') === 0) {
                if (isset($fv['error']) && $fv['error'] === UPLOAD_ERR_OK) {
                    $uploadedFiles[] = [
                        'name' => $fv['name'],
                        'tmp_name' => $fv['tmp_name']
                    ];
                }
            }
        }

        foreach ($uploadedFiles as $uFile) {
            $ext = strtolower(pathinfo($uFile['name'], PATHINFO_EXTENSION));
            $uuid = uniqid('media_', true);
            $relOrig = "uploads/originals/{$uuid}.{$ext}";
            $relThumb = "uploads/thumbs/{$uuid}.webp";

            move_uploaded_file($uFile['tmp_name'], __DIR__ . '/' . $relOrig);

            $isVideo = in_array($ext, ['mp4', 'mov', 'mkv', 'avi', '3gp', 'webm', 'm4v']);
            if (isset($_FILES['thumbnail']) && $_FILES['thumbnail']['error'] === UPLOAD_ERR_OK) {
                move_uploaded_file($_FILES['thumbnail']['tmp_name'], __DIR__ . '/' . $relThumb);
            } else if (!$isVideo && in_array($ext, ['jpg', 'jpeg', 'png', 'webp'])) {
                createThumbnail(__DIR__ . '/' . $relOrig, __DIR__ . '/' . $relThumb, 720);
            } else {
                $relThumb = $relOrig;
            }

            try {
                $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label) VALUES (?, ?, ?, ?, ?)");
                $stmtMedia->execute([$postId, $isVideo ? 'VIDEO' : 'IMAGE', $relOrig, $relThumb, $durationLabel ?: null]);
            } catch (Exception $eMedia) {
                $stmtFallback = $db->prepare("INSERT INTO media (post_id, media_type, file_path) VALUES (?, ?, ?)");
                $stmtFallback->execute([$postId, $isVideo ? 'VIDEO' : 'IMAGE', $relOrig]);
            }
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "post_id" => $postId]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 4.1. SUBIDA DE FRAGMENTOS DE VÍDEO
if ($method === 'POST' && matchRoute($action, $uri, 'video/upload_chunk')) {
    $sessionId = preg_replace('/[^a-zA-Z0-9_-]/', '', $_POST['session_id'] ?? '');
    $chunkIndex = (int)($_POST['chunk_index'] ?? 0);
    $totalChunks = (int)($_POST['total_chunks'] ?? 1);
    $fileKey = isset($_FILES['chunk']) ? 'chunk' : (isset($_FILES['file']) ? 'file' : null);

    if (empty($sessionId) || !$fileKey || !isset($_FILES[$fileKey])) {
        http_response_code(400);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "Falta session_id o archivo del fragmento"]);
        exit;
    }

    $chunkDir = __DIR__ . '/uploads/chunks/' . $sessionId;
    if (!is_dir($chunkDir)) @mkdir($chunkDir, 0777, true);
    $targetPath = $chunkDir . sprintf("/chunk_%04d.mp4", $chunkIndex);
    move_uploaded_file($_FILES[$fileKey]['tmp_name'], $targetPath);

    header("Content-Type: application/json; charset=UTF-8");
    echo json_encode(["status" => "OK", "session_id" => $sessionId, "chunk_index" => $chunkIndex, "received" => true]);
    exit;
}

// 4.2. FINALIZAR VÍDEO FRAGMENTADO
if ($method === 'POST' && matchRoute($action, $uri, 'video/finalize_chunks')) {
    $sessionId = preg_replace('/[^a-zA-Z0-9_-]/', '', $_POST['session_id'] ?? '');
    $userId = (int)($_POST['user_id'] ?? 1);
    $text = trim($_POST['content_text'] ?? '');
    $feeling = $_POST['feeling'] ?? null;
    $location = $_POST['location'] ?? null;
    $durationLabel = trim($_POST['duration_label'] ?? '');
    $isShort = (int)($_POST['is_short'] ?? 0);
    $groupId = !empty($_POST['group_id']) ? (int)$_POST['group_id'] : null;
    $totalChunks = (int)($_POST['total_chunks'] ?? 1);

    $chunkDir = __DIR__ . '/uploads/chunks/' . $sessionId;
    if (empty($sessionId) || !is_dir($chunkDir)) {
        http_response_code(400);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "No se encontró el directorio de fragmentos"]);
        exit;
    }

    try {
        $db = getDB();
        $db->beginTransaction();

        $stmt = $db->prepare("INSERT INTO posts (user_id, group_id, content_text, feeling, location, is_short) VALUES (?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $groupId, $text, $feeling, $location, $isShort]);
        $postId = (int)$db->lastInsertId();

        $thumbRel = "uploads/chunks/{$sessionId}/poster.jpg";
        $hasThumb = false;
        if (isset($_FILES['thumbnail']) && $_FILES['thumbnail']['error'] === UPLOAD_ERR_OK) {
            move_uploaded_file($_FILES['thumbnail']['tmp_name'], __DIR__ . '/' . $thumbRel);
            $hasThumb = true;
        }
        $firstChunkRel = "uploads/chunks/{$sessionId}/chunk_0000.mp4";
        if (!$hasThumb) $thumbRel = $firstChunkRel;

        $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label, is_chunked) VALUES (?, 'VIDEO', ?, ?, ?, 1)");
        $stmtMedia->execute([$postId, $firstChunkRel, $thumbRel, $durationLabel ?: null]);

        $stmtChunk = $db->prepare("INSERT INTO media_chunks (post_id, chunk_index, total_chunks, file_path) VALUES (?, ?, ?, ?)");
        for ($i = 0; $i < $totalChunks; $i++) {
            $stmtChunk->execute([$postId, $i, $totalChunks, sprintf("uploads/chunks/%s/chunk_%04d.mp4", $sessionId, $i)]);
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "post_id" => $postId, "total_chunks" => $totalChunks, "is_chunked" => true]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 5. HISTORIAS
if ($method === 'GET' && matchRoute($action, $uri, 'stories')) {
    try {
        $db = getDB();
        $stmt = $db->query("SELECT s.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex FROM stories s JOIN users u ON s.user_id = u.id ORDER BY s.created_at DESC LIMIT 30");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["stories" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

if ($method === 'POST' && (matchRoute($action, $uri, 'story') || matchRoute($action, $uri, 'stories'))) {
    $userId = (int)($_POST['user_id'] ?? 1);
    $caption = trim($_POST['caption'] ?? '');
    $storyType = $_POST['story_type'] ?? 'IMAGE';
    $bgColorHex = (int)($_POST['bg_color_hex'] ?? 4280171146);
    $relOrig = null;
    $relThumb = null;

    try {
        $db = getDB();
        if (isset($_FILES['photo']) && $_FILES['photo']['error'] === UPLOAD_ERR_OK) {
            $uploadDir = __DIR__ . '/uploads';
            if (!is_dir($uploadDir . '/originals')) @mkdir($uploadDir . '/originals', 0777, true);
            if (!is_dir($uploadDir . '/thumbs')) @mkdir($uploadDir . '/thumbs', 0777, true);

            $ext = strtolower(pathinfo($_FILES['photo']['name'], PATHINFO_EXTENSION));
            $uuid = uniqid('story_', true);
            $relOrig = "uploads/originals/{$uuid}.{$ext}";
            $relThumb = "uploads/thumbs/{$uuid}.webp";

            move_uploaded_file($_FILES['photo']['tmp_name'], __DIR__ . '/' . $relOrig);
            if (in_array($ext, ['jpg', 'jpeg', 'png', 'webp'])) {
                createThumbnail(__DIR__ . '/' . $relOrig, __DIR__ . '/' . $relThumb, 720);
            } else {
                $relThumb = $relOrig;
            }
        }

        $stmt = $db->prepare("INSERT INTO stories (user_id, caption, story_type, file_path, thumb_path, bg_color_hex) VALUES (?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $caption, $storyType, $relOrig, $relThumb, $bgColorHex]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "story_id" => (int)$db->lastInsertId()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 6. REACCIONES
if ($method === 'POST' && matchRoute($action, $uri, 'reaction')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $postId = (int)($input['post_id'] ?? 0);
    $userId = (int)($input['user_id'] ?? 0);
    $rxType = $input['reaction_type'] ?? 'LIKE';

    try {
        $db = getDB();
        $check = $db->prepare("SELECT COUNT(*) FROM reactions WHERE post_id = ? AND user_id = ?");
        $check->execute([$postId, $userId]);
        if ($check->fetchColumn() > 0) {
            $db->prepare("DELETE FROM reactions WHERE post_id = ? AND user_id = ?")->execute([$postId, $userId]);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["action" => "removed"]);
        } else {
            $db->prepare("INSERT INTO reactions (post_id, user_id, reaction_type) VALUES (?, ?, ?)")->execute([$postId, $userId, $rxType]);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["action" => "added"]);
        }
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 7. COMENTARIOS
if ($method === 'POST' && matchRoute($action, $uri, 'comment')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $postId = (int)($input['post_id'] ?? 0);
    $userId = (int)($input['user_id'] ?? 0);
    $text = trim($input['comment_text'] ?? '');

    try {
        $db = getDB();
        $stmt = $db->prepare("INSERT INTO comments (post_id, user_id, comment_text) VALUES (?, ?, ?)");
        $stmt->execute([$postId, $userId, $text]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "comment_id" => (int)$db->lastInsertId()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

if ($method === 'GET' && matchRoute($action, $uri, 'comments')) {
    $postId = (int)($_GET['post_id'] ?? 0);
    try {
        $db = getDB();
        $stmt = $db->prepare("SELECT c.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex FROM comments c JOIN users u ON c.user_id = u.id WHERE c.post_id = ? ORDER BY c.created_at ASC");
        $stmt->execute([$postId]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["comments" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 8. ELIMINAR POST (Soporta DELETE, POST y GET)
if (($method === 'DELETE' && (str_contains($uri, '/post') || !empty($_GET['post_id']))) ||
    ($method === 'POST' && (matchRoute($action, $uri, 'delete_post') || matchRoute($action, $uri, 'post/delete'))) ||
    ($method === 'GET' && matchRoute($action, $uri, 'delete_post'))) {
    $delPostId = (int)($_GET['post_id'] ?? $_POST['post_id'] ?? 0);
    if ($delPostId === 0 && preg_match('#/post/(\\d+)#', $uri, $m)) $delPostId = (int)$m[1];

    if ($delPostId > 0) {
        try {
            $db = getDB();
            $mediaStmt = $db->prepare("SELECT file_path, thumb_path FROM media WHERE post_id = ?");
            $mediaStmt->execute([$delPostId]);
            while ($m = $mediaStmt->fetch()) {
                if (!empty($m['file_path']) && file_exists(__DIR__ . '/' . $m['file_path'])) @unlink(__DIR__ . '/' . $m['file_path']);
                if (!empty($m['thumb_path']) && file_exists(__DIR__ . '/' . $m['thumb_path'])) @unlink(__DIR__ . '/' . $m['thumb_path']);
            }
            $db->prepare("DELETE FROM posts WHERE id = ?")->execute([$delPostId]);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["success" => true, "deleted_id" => $delPostId]);
        } catch (Exception $e) {
            http_response_code(500);
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["error" => $e->getMessage()]);
        }
        exit;
    }
}

// 9. ESCANEAR CARPETAS DEL NAS
function collectFolderMediaRecursive($dir, $maxFiles = 10000, $maxDepth = 5) {
    $results = [];
    if (!@is_dir($dir)) return $results;
    @set_time_limit(180);

    $imgExts = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp', 'heic'];
    $vidExts = ['mp4', 'mov', 'mkv', 'avi', '3gp', 'webm', 'm4v'];

    try {
        $dirIterator = new RecursiveDirectoryIterator($dir, RecursiveDirectoryIterator::SKIP_DOTS);
        $iterator = new RecursiveIteratorIterator($dirIterator, RecursiveIteratorIterator::SELF_FIRST);
        $iterator->setMaxDepth($maxDepth);

        foreach ($iterator as $item) {
            if (count($results) >= $maxFiles) break;
            $name = $item->getFilename();
            if ($name === '@eaDir' || $name === '#recycle' || $name === '@tmp' || $name === '.synologywork' || strpos($name, '.') === 0) continue;
            $path = $item->getPathname();
            if (strpos($path, '/@eaDir') !== false || strpos($path, '/#recycle') !== false) continue;
            if ($item->isFile()) {
                $ext = strtolower($item->getExtension());
                $subDir = dirname(substr($path, strlen($dir) + 1));
                $relSub = ($subDir !== '.' && $subDir !== '') ? str_replace('\\', '/', $subDir) : '';
                if (in_array($ext, $imgExts)) {
                    $results[] = ["path" => $path, "type" => "IMAGE", "name" => pathinfo($item->getFilename(), PATHINFO_FILENAME), "rel_subfolder" => $relSub];
                } else if (in_array($ext, $vidExts)) {
                    $results[] = ["path" => $path, "type" => "VIDEO", "name" => pathinfo($item->getFilename(), PATHINFO_FILENAME), "rel_subfolder" => $relSub];
                }
            }
        }
    } catch (Exception $e) {
        $files = @scandir($dir);
        if ($files !== false) {
            foreach ($files as $f) {
                if ($f === '.' || $f === '..' || $f === '@eaDir' || $f === '#recycle' || strpos($f, '.') === 0) continue;
                $full = $dir . '/' . $f;
                if (@is_file($full)) {
                    $ext = strtolower(pathinfo($f, PATHINFO_EXTENSION));
                    if (in_array($ext, $imgExts)) $results[] = ["path" => $full, "type" => "IMAGE", "name" => pathinfo($f, PATHINFO_FILENAME), "rel_subfolder" => ''];
                    elseif (in_array($ext, $vidExts)) $results[] = ["path" => $full, "type" => "VIDEO", "name" => pathinfo($f, PATHINFO_FILENAME), "rel_subfolder" => ''];
                }
            }
        }
    }
    return $results;
}

// 9. DETECCIÓN DE DISCOS Y VOLÚMENES EN SYNOLOGY NAS (/volume1, /volume2, /volume3...)
if (($method === 'GET' || $method === 'POST') && (matchRoute($action, $uri, 'admin/detect_storage') || matchRoute($action, $uri, 'detect_volumes') || matchRoute($action, $uri, 'admin/volumes'))) {
    $volumes = [];
    $candidateVolumes = [];
    
    // Detectar volumen actual donde está instalado el script PHP
    $currentDir = __DIR__;
    if (preg_match('#^(/volume[0-9a-zA-Z_-]+)#', $currentDir, $m)) {
        $candidateVolumes[] = $m[1];
    }
    
    // Probar volúmenes estándar de Synology DSM
    for ($i = 1; $i <= 12; $i++) {
        $v = "/volume" . $i;
        if (!in_array($v, $candidateVolumes)) $candidateVolumes[] = $v;
    }
    $candidateVolumes[] = "/volumeUSB1";
    $candidateVolumes[] = "/volumeUSB2";

    $suggestedPath = "";

    foreach ($candidateVolumes as $volPath) {
        if (@is_dir($volPath) && @is_readable($volPath)) {
            $totalBytes = @disk_total_space($volPath);
            $freeBytes = @disk_free_space($volPath);
            $totalGB = $totalBytes ? round($totalBytes / (1024 * 1024 * 1024), 1) : 0;
            $freeGB = $freeBytes ? round($freeBytes / (1024 * 1024 * 1024), 1) : 0;
            
            $mediaCandidates = [
                $volPath . '/photo',
                $volPath . '/home/Photos',
                $volPath . '/video',
                $volPath . '/public',
                $volPath . '/web',
                $volPath . '/media'
            ];
            $availableFolders = [];
            foreach ($mediaCandidates as $mc) {
                if (@is_dir($mc) && @is_readable($mc)) {
                    $availableFolders[] = $mc;
                    if (empty($suggestedPath)) {
                        $suggestedPath = $mc;
                    }
                }
            }

            if (empty($suggestedPath)) {
                $suggestedPath = $volPath;
            }

            $volumes[] = [
                "volume" => basename($volPath),
                "path" => $volPath,
                "total_gb" => $totalGB,
                "free_gb" => $freeGB,
                "is_current_server_volume" => (strpos($currentDir, $volPath) === 0),
                "media_folders" => $availableFolders
            ];
        }
    }

    if (empty($suggestedPath)) {
        $suggestedPath = "/volume3/photo";
    }

    header("Content-Type: application/json; charset=UTF-8");
    echo json_encode([
        "status" => "OK",
        "volumes_count" => count($volumes),
        "volumes" => $volumes,
        "suggested_path" => $suggestedPath,
        "server_volume" => (preg_match('#^(/volume[0-9a-zA-Z_-]+)#', __DIR__, $m) ? $m[1] : "/volume3")
    ]);
    exit;
}

if (($method === 'GET' || $method === 'POST') && matchRoute($action, $uri, 'admin/scan_folders')) {
    $basePath = trim($_GET['base_path'] ?? $_POST['base_path'] ?? '');
    
    // Si la ruta solicitada no existe, buscar automáticamente en los volúmenes disponibles (ej: /volume3/photo o /volume3)
    if (empty($basePath) || !@is_dir($basePath)) {
        $fallbackPaths = [
            $basePath,
            '/volume3/photo',
            '/volume3/home/Photos',
            '/volume3/video',
            '/volume3',
            '/volume1/photo',
            '/volume1',
            '/volume2/photo',
            '/volume2'
        ];
        foreach ($fallbackPaths as $fp) {
            if (!empty($fp) && @is_dir($fp) && @is_readable($fp)) {
                $basePath = $fp;
                break;
            }
        }
    }
    if (empty($basePath)) $basePath = '/volume3/photo';

    if (!@is_dir($basePath)) {
        http_response_code(404);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "La ruta '{$basePath}' no existe o no tiene permisos de lectura. Comprueba el disco del servidor (ej. /volume3/photo)."]);
        exit;
    }

    try {
        $db = getDB();
        $existingPaths = [];
        try {
            $existingPaths = $db->query("SELECT folder_path FROM family_groups")->fetchAll(PDO::FETCH_COLUMN) ?: [];
        } catch (Exception $ex) {}

        $entries = @scandir($basePath);
        $folders = [];
        if ($entries !== false) {
            foreach ($entries as $entry) {
                if ($entry === '.' || $entry === '..' || $entry === '@eaDir' || $entry === '#recycle') continue;
                $fullPath = rtrim($basePath, '/') . '/' . $entry;
                if (@is_dir($fullPath)) {
                    $mediaList = collectFolderMediaRecursive($fullPath, 5000);
                    $photoCount = 0; $videoCount = 0;
                    foreach ($mediaList as $mItem) {
                        if ($mItem['type'] === 'IMAGE') $photoCount++;
                        else if ($mItem['type'] === 'VIDEO') $videoCount++;
                    }
                    $folders[] = [
                        "name" => $entry, "path" => $fullPath,
                        "photos_count" => $photoCount, "videos_count" => $videoCount,
                        "media_count" => ($photoCount + $videoCount),
                        "has_subfolders" => true,
                        "already_imported" => in_array($fullPath, $existingPaths)
                    ];
                }
            }
        }
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["status" => "OK", "base_path" => $basePath, "folders" => $folders]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 10. IMPORTAR CARPETAS COMO GRUPOS (POR LOTES PROGRESIVOS)
if ($method === 'POST' && matchRoute($action, $uri, 'admin/import_groups')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $folderPaths = $input['folder_paths'] ?? [];
    if (is_string($folderPaths)) $folderPaths = explode(',', $folderPaths);
    $adminUserId = (int)($input['admin_user_id'] ?? 1);
    $batchOffset = (int)($input['batch_offset'] ?? 0);
    $batchLimit = (int)($input['batch_limit'] ?? 50);
    $isBatch = !empty($input['is_batch']) || isset($input['batch_offset']);

    if (empty($folderPaths) || !is_array($folderPaths)) {
        http_response_code(400);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "No se recibieron carpetas"]);
        exit;
    }

    try {
        $db = getDB();
        $db->beginTransaction();
        $importedGroups = 0; $importedPosts = 0; $totalFiles = 0;
        $hasMore = false; $nextOffset = 0;

        foreach ($folderPaths as $fPath) {
            $fPath = trim($fPath);
            if (empty($fPath) || !@is_dir($fPath)) continue;

            $groupName = basename($fPath);
            $check = $db->prepare("SELECT id FROM family_groups WHERE folder_path = ? LIMIT 1");
            $check->execute([$fPath]);
            $existingId = $check->fetchColumn();

            if ($existingId) {
                $groupId = (int)$existingId;
            } else {
                $groupStmt = $db->prepare("INSERT INTO family_groups (name, folder_path, description) VALUES (?, ?, ?)");
                $groupStmt->execute([$groupName, $fPath, "Grupo familiar sincronizado con subcarpetas"]);
                $groupId = (int)$db->lastInsertId();
            }

            $allMedia = collectFolderMediaRecursive($fPath);
            $totalInFolder = count($allMedia);
            $totalFiles += $totalInFolder;

            $slice = $isBatch ? array_slice($allMedia, $batchOffset, $batchLimit) : $allMedia;
            $coverSet = false;

            foreach ($slice as $mediaInfo) {
                $fullFile = $mediaInfo['path'];
                $isVid = ($mediaInfo['type'] === 'VIDEO');
                $isImg = ($mediaInfo['type'] === 'IMAGE');
                $proxyUrl = "index.php?action=media_proxy&path=" . urlencode($fullFile);

                $checkMedia = $db->prepare("SELECT id FROM media WHERE file_path = ? LIMIT 1");
                $checkMedia->execute([$proxyUrl]);
                if ($checkMedia->fetchColumn()) continue;

                $caption = $mediaInfo['name'];
                if (!empty($mediaInfo['rel_subfolder'])) $caption .= " (" . $mediaInfo['rel_subfolder'] . ")";

                $stmtPost = $db->prepare("INSERT INTO posts (user_id, group_id, content_text, is_short) VALUES (?, ?, ?, 0)");
                $stmtPost->execute([$adminUserId, $groupId, $caption]);
                $newPostId = (int)$db->lastInsertId();

                $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path) VALUES (?, ?, ?, ?)");
                $stmtMedia->execute([$newPostId, $isVid ? 'VIDEO' : 'IMAGE', $proxyUrl, $proxyUrl]);
                $importedPosts++;

                if (!$coverSet && $isImg) {
                    $db->prepare("UPDATE family_groups SET cover_photo_url = ? WHERE id = ?")->execute([$proxyUrl, $groupId]);
                    $coverSet = true;
                }
            }

            if ($isBatch) {
                $currentProcessed = $batchOffset + count($slice);
                if ($currentProcessed < $totalInFolder) {
                    $hasMore = true;
                    $nextOffset = $currentProcessed;
                }
            }

            $db->prepare("UPDATE family_groups SET posts_count = (SELECT COUNT(*) FROM posts WHERE group_id = ?) WHERE id = ?")->execute([$groupId, $groupId]);
            $importedGroups++;
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => "OK", "imported_groups" => $importedGroups, "imported_posts" => $importedPosts,
            "batch_offset" => $batchOffset, "batch_imported" => $importedPosts, "total_files" => $totalFiles,
            "has_more" => $hasMore, "next_offset" => $nextOffset
        ]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

function createThumbnail($src, $dest, $maxW = 720) {
    list($w, $h, $type) = @getimagesize($src);
    if (!$w || !$h) return;
    $ratio = $h / $w;
    $nw = min($w, $maxW);
    $nh = (int)($nw * $ratio);

    $im = null;
    switch ($type) {
        case IMAGETYPE_JPEG: $im = @imagecreatefromjpeg($src); break;
        case IMAGETYPE_PNG: $im = @imagecreatefrompng($src); break;
        default:
            if (defined('IMAGETYPE_WEBP') && $type === IMAGETYPE_WEBP && function_exists('imagecreatefromwebp')) {
                $im = @imagecreatefromwebp($src);
            }
            break;
    }
    if (!$im) return;

    $thumb = imagecreatetruecolor($nw, $nh);
    imagecopyresampled($thumb, $im, 0, 0, 0, 0, $nw, $nh, $w, $h);
    if (function_exists('imagewebp')) imagewebp($thumb, $dest, 80);
    else imagejpeg($thumb, $dest, 80);
    imagedestroy($im);
    imagedestroy($thumb);
}

header("Content-Type: application/json; charset=UTF-8");
echo json_encode(["message" => "Endpoint no encontrado"]);
