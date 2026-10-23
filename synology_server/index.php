<?php
/**
 * index.php - API REST para Muro Familiar LAN
 * Servir vía Web Station en puerto dedicado (ej: 8085)
 * Ruta en NAS: /volume1/web/family_api/index.php
 */

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");
header("Content-Type: application/json; charset=UTF-8");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once __DIR__ . '/config.php';

$uri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$method = $_SERVER['REQUEST_METHOD'];

// 1. Health check / Ping
if (str_ends_with($uri, '/ping') || str_ends_with($uri, '/health')) {
    try {
        $db = getDB();
        echo json_encode([
            "status" => "ONLINE",
            "server" => "Synology Web Station + MariaDB 10",
            "time" => date("Y-m-d H:i:s")
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(["status" => "ERROR", "message" => $e->getMessage()]);
    }
    exit;
}

// 2. OBTENER USUARIOS
if ($method === 'GET' && str_ends_with($uri, '/users')) {
    try {
        $db = getDB();
        $stmt = $db->query("SELECT id, display_name, role, avatar_emoji, avatar_color_hex, bio, pin_hash FROM users ORDER BY id ASC");
        echo json_encode($stmt->fetchAll());
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3. OBTENER FEED COMPLETO
if ($method === 'GET' && (str_ends_with($uri, '/feed') || str_ends_with($uri, '/posts'))) {
    try {
        $db = getDB();
        $stmt = $db->query("
            SELECT p.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex,
                   m.media_type, m.file_path, m.thumb_path, m.duration_label,
                   (SELECT COUNT(*) FROM reactions r WHERE r.post_id = p.id) as likes_count
            FROM posts p
            JOIN users u ON p.user_id = u.id
            LEFT JOIN media m ON m.post_id = p.id
            ORDER BY p.is_pinned DESC, p.created_at DESC
            LIMIT 50
        ");
        $posts = $stmt->fetchAll();
        echo json_encode(["posts" => $posts]);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 4. CREAR PUBLICACIÓN CON ARCHIVO REAL
if ($method === 'POST' && (str_ends_with($uri, '/post') || str_ends_with($uri, '/posts'))) {
    $userId = (int)($_POST['user_id'] ?? 1);
    $text = trim($_POST['content_text'] ?? '');
    $feeling = $_POST['feeling'] ?? null;
    $location = $_POST['location'] ?? null;

    try {
        $db = getDB();
        $db->beginTransaction();

        $stmt = $db->prepare("INSERT INTO posts (user_id, content_text, feeling, location) VALUES (?, ?, ?, ?)");
        $stmt->execute([$userId, $text, $feeling, $location]);
        $postId = $db->lastInsertId();

        if (isset($_FILES['photo']) && $_FILES['photo']['error'] === UPLOAD_ERR_OK) {
            $uploadDir = __DIR__ . '/uploads';
            if (!is_dir($uploadDir . '/originals')) mkdir($uploadDir . '/originals', 0777, true);
            if (!is_dir($uploadDir . '/thumbs')) mkdir($uploadDir . '/thumbs', 0777, true);

            $ext = strtolower(pathinfo($_FILES['photo']['name'], PATHINFO_EXTENSION));
            $uuid = uniqid('media_', true);
            $relOrig = "uploads/originals/{$uuid}.{$ext}";
            $relThumb = "uploads/thumbs/{$uuid}.webp";

            move_uploaded_file($_FILES['photo']['tmp_name'], __DIR__ . '/' . $relOrig);

            // Generar WebP thumbnail con GD si es imagen
            if (in_array($ext, ['jpg', 'jpeg', 'png', 'webp'])) {
                createThumbnail(__DIR__ . '/' . $relOrig, __DIR__ . '/' . $relThumb, 720);
            } else {
                $relThumb = $relOrig;
            }

            $isVideo = in_array($ext, ['mp4', 'mov', 'mkv', 'avi']);
            $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path) VALUES (?, ?, ?, ?)");
            $stmtMedia->execute([$postId, $isVideo ? 'VIDEO' : 'IMAGE', $relOrig, $relThumb]);
        }

        $db->commit();
        echo json_encode(["success" => true, "post_id" => (int)$postId]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 5. REACCIÓN REAL
if ($method === 'POST' && str_ends_with($uri, '/reaction')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $postId = (int)($input['post_id'] ?? 0);
    $userId = (int)($input['user_id'] ?? 0);
    $rxType = $input['reaction_type'] ?? 'LIKE';

    try {
        $db = getDB();
        $check = $db->prepare("SELECT COUNT(*) FROM reactions WHERE post_id = ? AND user_id = ?");
        $check->execute([$postId, $userId]);
        if ($check->fetchColumn() > 0) {
            $del = $db->prepare("DELETE FROM reactions WHERE post_id = ? AND user_id = ?");
            $del->execute([$postId, $userId]);
            echo json_encode(["action" => "removed"]);
        } else {
            $ins = $db->prepare("INSERT INTO reactions (post_id, user_id, reaction_type) VALUES (?, ?, ?)");
            $ins->execute([$postId, $userId, $rxType]);
            echo json_encode(["action" => "added"]);
        }
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 6. COMENTARIO REAL
if ($method === 'POST' && str_ends_with($uri, '/comment')) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $postId = (int)($input['post_id'] ?? 0);
    $userId = (int)($input['user_id'] ?? 0);
    $text = trim($input['comment_text'] ?? '');

    try {
        $db = getDB();
        $stmt = $db->prepare("INSERT INTO comments (post_id, user_id, comment_text) VALUES (?, ?, ?)");
        $stmt->execute([$postId, $userId, $text]);
        echo json_encode(["success" => true, "comment_id" => $db->lastInsertId()]);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

function createThumbnail($src, $dest, $maxW = 720) {
    list($w, $h, $type) = @getimagesize($src);
    if (!$w) return;
    $ratio = $h / $w;
    $nw = min($w, $maxW);
    $nh = (int)($nw * $ratio);

    $im = match($type) {
        IMAGETYPE_JPEG => @imagecreatefromjpeg($src),
        IMAGETYPE_PNG  => @imagecreatefrompng($src),
        IMAGETYPE_WEBP => @imagecreatefromwebp($src),
        default        => null
    };
    if (!$im) return;

    $thumb = imagecreatetruecolor($nw, $nh);
    imagecopyresampled($thumb, $im, 0, 0, 0, 0, $nw, $nh, $w, $h);
    imagewebp($thumb, $dest, 80);
    imagedestroy($im);
    imagedestroy($thumb);
}

echo json_encode(["message" => "Endpoint no encontrado"]);
