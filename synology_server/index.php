<?php
/**
 * index.php - API REST para Muro Familiar LAN
 * Totalmente compatible con PHP 7.3 y MariaDB 10 en Synology DSM 6 y DSM 7
 * Servir vía Web Station en puerto dedicado (ej: 8085 o puerto 80)
 * Ruta en NAS: /volume1/web/family_api/index.php
 */

ini_set('display_errors', '0');
error_reporting(E_ALL);

@set_time_limit(0);
@ini_set('max_execution_time', '0');
@ini_set('max_input_time', '0');
@ini_set('memory_limit', '2048M');
@ini_set('upload_max_filesize', '4096M');
@ini_set('post_max_size', '4096M');

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

// Manejador global de excepciones (siempre devuelve JSON estructurado)
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

// Captura de errores fatales de parseo o memoria en PHP 7.3
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

// Función de enrutamiento estricta: evita que 'group/posts' coincida erróneamente con 'posts'
function matchRoute($action, $uri, $target) {
    $t = trim($target, '/');
    if (!empty($action)) {
        return $action === $t;
    }
    $pattern = '#/(?:index\\.php/)?' . preg_quote($t, '#') . '(?:$|\\?)#';
    return preg_match($pattern, $uri) === 1;
}

/**
 * Función de transmisión multimedia con soporte completo de HTTP Range (HTTP 206 Partial Content).
 * Permite reproducción fluida en ExoPlayer, saltos temporales (seeking) y previene agotamiento de memoria en PHP 7.3.
 */
function streamMediaFile($filePath) {
    if (!@file_exists($filePath) || !@is_readable($filePath)) {
        http_response_code(404);
        header('Content-Type: application/json; charset=UTF-8');
        echo json_encode(["error" => "Archivo multimedia no encontrado o sin permisos en el NAS"]);
        exit;
    }

    $size = filesize($filePath);
    $ext = strtolower(pathinfo($filePath, PATHINFO_EXTENSION));
    $mimes = [
        'mp4' => 'video/mp4', 'mov' => 'video/quicktime', 'mkv' => 'video/x-matroska',
        'webm' => 'video/webm', 'avi' => 'video/x-msvideo', '3gp' => 'video/3gpp', 'm4v' => 'video/mp4',
        'jpg' => 'image/jpeg', 'jpeg' => 'image/jpeg', 'png' => 'image/png',
        'webp' => 'image/webp', 'gif' => 'image/gif', 'bmp' => 'image/bmp'
    ];
    $mime = $mimes[$ext] ?? 'application/octet-stream';

    // Limpiar buffers de salida previos para evitar consumo de memoria
    while (ob_get_level()) {
        @ob_end_clean();
    }

    $start = 0;
    $end = $size - 1;

    if (isset($_SERVER['HTTP_RANGE'])) {
        $c_start = $start;
        $c_end = $end;
        list(, $range) = explode('=', $_SERVER['HTTP_RANGE'], 2);
        if (strpos($range, ',') !== false) {
            header('HTTP/1.1 416 Requested Range Not Satisfiable');
            header("Content-Range: bytes $start-$end/$size");
            exit;
        }
        if ($range == '-') {
            $c_start = $size - substr($range, 1);
        } else {
            $range = explode('-', $range);
            $c_start = $range[0];
            $c_end = (isset($range[1]) && is_numeric($range[1])) ? $range[1] : $size - 1;
        }
        $c_end = ($c_end > $end) ? $end : $c_end;
        if ($c_start > $c_end || $c_start > $size - 1 || $c_end >= $size) {
            header('HTTP/1.1 416 Requested Range Not Satisfiable');
            header("Content-Range: bytes $start-$end/$size");
            exit;
        }
        $start = $c_start;
        $end = $c_end;
        $length = $end - $start + 1;
        header('HTTP/1.1 206 Partial Content');
        header("Content-Range: bytes $start-$end/$size");
        header("Content-Length: $length");
    } else {
        header('HTTP/1.1 200 OK');
        header("Content-Length: $size");
    }

    header("Content-Type: $mime");
    header('Accept-Ranges: bytes');
    header('Cache-Control: public, max-age=86400');

    $fp = @fopen($filePath, 'rb');
    if (!$fp) {
        http_response_code(500);
        header('Content-Type: application/json; charset=UTF-8');
        echo json_encode(["error" => "No se pudo abrir el archivo para lectura"]);
        exit;
    }

    fseek($fp, $start);
    $buffer = 64 * 1024; // Transmisión en bloques de 64 KB
    while (!feof($fp) && ($pos = ftell($fp)) <= $end) {
        if ($pos + $buffer > $end) {
            $buffer = $end - $pos + 1;
        }
        echo fread($fp, $buffer);
        flush();
    }
    fclose($fp);
    exit;
}

// 1. Health check / Ping
if (matchRoute($action, $uri, 'ping') || matchRoute($action, $uri, 'health')) {
    try {
        $db = getDB();
        $serverVer = "";
        try {
            $serverVer = $db->getAttribute(PDO::ATTR_SERVER_VERSION);
        } catch (Exception $eVer) {}
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => "ONLINE",
            "server" => "Synology Web Station",
            "php_version" => PHP_VERSION,
            "database" => DB_NAME,
            "database_version" => $serverVer,
            "time" => date("Y-m-d H:i:s")
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["status" => "ERROR", "error" => $e->getMessage(), "message" => $e->getMessage()]);
    }
    exit;
}

// 1.0. DIAGNÓSTICO EXHAUSTIVO DE CONEXIÓN CON MARIADB 10 (Nunca retorna 500; reporta estado detallado)
if (matchRoute($action, $uri, 'db_diagnostics') || matchRoute($action, $uri, 'diagnostics')) {
    header("Content-Type: application/json; charset=UTF-8");
    // Detectar volumen y entorno actual
    $currentDir = __DIR__;
    preg_match('#/volume([0-9]+)/#', $currentDir, $volMatches);
    $detectedVol = isset($volMatches[1]) ? "volume" . $volMatches[1] : "volumen del sistema";
    $openBaseDir = ini_get('open_basedir') ?: 'Ninguno (sin restricciones)';

    $report = [
        "status" => "DIAGNOSTIC_REPORT",
        "timestamp" => date("Y-m-d H:i:s"),
        "php_version" => PHP_VERSION,
        "environment" => [
            "current_directory" => $currentDir,
            "server_volume" => $detectedVol,
            "open_basedir" => $openBaseDir,
            "server_addr" => $_SERVER['SERVER_ADDR'] ?? '127.0.0.1'
        ],
        "config" => [
            "db_host" => DB_HOST,
            "db_port" => (int)DB_PORT,
            "db_name" => DB_NAME,
            "db_user" => DB_USER,
            "db_pass_is_default" => (DB_PASS === 'TuClaveSynology123' || empty(DB_PASS))
        ],
        "checks" => []
    ];

    // Check 1: Extensiones PDO y MySQL
    $pdoLoaded = extension_loaded('pdo');
    $pdoMysqlLoaded = extension_loaded('pdo_mysql');
    $mysqliLoaded = extension_loaded('mysqli');
    $report["checks"]["php_extensions"] = [
        "success" => ($pdoLoaded && $pdoMysqlLoaded),
        "pdo" => $pdoLoaded,
        "pdo_mysql" => $pdoMysqlLoaded,
        "mysqli" => $mysqliLoaded,
        "message" => ($pdoLoaded && $pdoMysqlLoaded) 
            ? "Extensiones PDO y pdo_mysql activas en PHP 7.3." 
            : "FALTA la extensión pdo_mysql. Ve a DSM -> Web Station -> Configuración de lenguaje de script -> PHP 7.3 -> Extensiones y marca 'pdo_mysql'."
    ];

    // Check 2: Conectividad TCP al puerto de MariaDB (3307)
    $tcpOk = false;
    $tcpErr = "";
    $tcpSocket = @fsockopen(DB_HOST, (int)DB_PORT, $errno, $errstr, 2.0);
    if ($tcpSocket) {
        $tcpOk = true;
        @fclose($tcpSocket);
    } else {
        $tcpErr = "$errstr ($errno)";
    }
    // Probar también si el socket responde en la IP local del propio NAS
    $serverIpTcp = false;
    if (!$tcpOk && !empty($_SERVER['SERVER_ADDR'])) {
        $sSocket = @fsockopen($_SERVER['SERVER_ADDR'], (int)DB_PORT, $sErrno, $sErrstr, 1.5);
        if ($sSocket) {
            $serverIpTcp = true;
            $tcpOk = true;
            @fclose($sSocket);
        }
    }
    $report["checks"]["tcp_connection"] = [
        "success" => $tcpOk,
        "target" => DB_HOST . ":" . DB_PORT,
        "error" => $tcpErr,
        "message" => $tcpOk 
            ? "Puerto TCP " . DB_PORT . " respondiendo correctamente en MariaDB 10."
            : "No se pudo conectar por TCP al puerto " . DB_PORT . ". Verifica que el paquete MariaDB 10 esté 'En ejecución' en el Centro de Paquetes de DSM."
    ];

    // Check 3: Sockets UNIX locales en Synology y en volúmenes
    $knownSockets = [
        "/run/mysqld/mysqld10.sock",
        "/var/run/mysqld/mysqld10.sock",
        "/run/mysqld/mysqld.sock",
        "/var/run/mysqld/mysqld.sock",
        "/tmp/mysql.sock"
    ];
    for ($v = 1; $v <= 8; $v++) {
        $knownSockets[] = "/volume$v/@appdata/MariaDB10/mysqld.sock";
        $knownSockets[] = "/volume$v/@appstore/MariaDB10/mysqld.sock";
    }
    $foundSockets = [];
    foreach ($knownSockets as $sock) {
        if (@file_exists($sock)) {
            $foundSockets[] = $sock;
        }
    }
    $report["checks"]["unix_sockets"] = [
        "success" => (count($foundSockets) > 0),
        "found" => $foundSockets,
        "message" => (count($foundSockets) > 0)
            ? "Socket(s) MariaDB detectado(s): " . implode(", ", $foundSockets)
            : "No se detectaron sockets accesibles (normal si open_basedir está restringido o se conecta por TCP 3307)."
    ];

    // Check 4: Autenticación y ejecución de consultas en MariaDB
    $authSuccess = false;
    $authError = "";
    $primaryError = "";
    $connectedDsn = "";
    $serverVer = "";
    $dbExists = false;
    $tables = [];
    $attempts = [];

    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_TIMEOUT => 4
    ];

    $testTargets = [
        ["label" => "TCP " . DB_HOST . ":" . DB_PORT, "dsn" => "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";charset=utf8mb4"],
    ];
    if (!empty($_SERVER['SERVER_ADDR']) && $_SERVER['SERVER_ADDR'] !== DB_HOST) {
        $testTargets[] = ["label" => "TCP " . $_SERVER['SERVER_ADDR'] . ":" . DB_PORT, "dsn" => "mysql:host=" . $_SERVER['SERVER_ADDR'] . ";port=" . DB_PORT . ";charset=utf8mb4"];
    }
    $testTargets[] = ["label" => "Socket mysqld10.sock", "dsn" => "mysql:unix_socket=/run/mysqld/mysqld10.sock;charset=utf8mb4"];
    $testTargets[] = ["label" => "Socket var mysqld10.sock", "dsn" => "mysql:unix_socket=/var/run/mysqld/mysqld10.sock;charset=utf8mb4"];

    $diagPdo = null;
    foreach ($testTargets as $idx => $tgt) {
        $dsn = $tgt["dsn"];
        $label = $tgt["label"];
        try {
            $tempPdo = new PDO($dsn, DB_USER, DB_PASS, $options);
            // Si conecta al servidor, asegurar la base de datos y seleccionarla
            $tempPdo->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            $tempPdo->exec("USE `" . DB_NAME . "`;");

            $diagPdo = $tempPdo;
            $connectedDsn = $dsn;
            $authSuccess = true;
            $dbExists = true;
            $attempts[] = ["target" => $label, "success" => true, "detail" => "Conexión y base de datos seleccionadas con éxito"];
            break;
        } catch (PDOException $e) {
            $errMsg = $e->getMessage();
            $attempts[] = ["target" => $label, "success" => false, "detail" => $errMsg];
            if ($primaryError === "") {
                $primaryError = $errMsg;
            }
            $authError = $errMsg;
        }
    }

    $expectedTables = ['users', 'family_groups', 'posts', 'media', 'media_chunks', 'comments', 'reactions', 'stories'];
    if ($authSuccess && $diagPdo) {
        try {
            $serverVer = $diagPdo->getAttribute(PDO::ATTR_SERVER_VERSION);
            autoBootstrapDatabase($diagPdo);
            $st = $diagPdo->query("SHOW TABLES");
            if ($st) {
                $tables = $st->fetchAll(PDO::FETCH_COLUMN);
            }
        } catch (Exception $ex) {}
    }

    $missingTables = array_values(array_diff($expectedTables, $tables));
    $effectiveError = !empty($primaryError) ? $primaryError : $authError;

    $report["checks"]["mariadb_authentication"] = [
        "success" => $authSuccess,
        "connected_dsn" => $connectedDsn,
        "server_version" => $serverVer,
        "database_exists" => $dbExists,
        "tables_count" => count($tables),
        "expected_tables_count" => 8,
        "tables" => $tables,
        "missing_tables" => $missingTables,
        "attempts" => $attempts,
        "error" => $effectiveError,
        "message" => $authSuccess 
            ? "Conexión y autenticación exitosa con MariaDB ($serverVer). Tablas encontradas: " . count($tables) . "/8 (" . implode(', ', $tables) . ")"
            : "Fallo de autenticación o conexión. " . ($report["config"]["db_pass_is_default"] ? "¡ATENCIÓN! config.php aún tiene la clave genérica 'TuClaveSynology123'. Edita config.php y coloca la contraseña de MariaDB." : "Detalle: $effectiveError")
    ];

    // Recomendación general accionable adaptada a DSM 7 y cualquier volumen
    if (!$report["checks"]["php_extensions"]["success"]) {
        $report["recommendation"] = "Falta pdo_mysql: Ve a DSM -> Web Station -> Configuración de lenguaje de script -> PHP 7.3 -> Extensiones y marca 'pdo_mysql'.";
    } elseif ($report["config"]["db_pass_is_default"]) {
        $report["recommendation"] = "Contraseña por defecto en $currentDir/config.php: En DSM 7 MariaDB 10 siempre escucha en el puerto 3307; abre config.php y cambia 'TuClaveSynology123' por la clave que pusiste en MariaDB.";
    } elseif (!$authSuccess) {
        if (stripos($effectiveError, 'Access denied') !== false || stripos($effectiveError, 'Acceso denegado') !== false || stripos($effectiveError, '1045') !== false || stripos($effectiveError, 'using password') !== false) {
            $report["recommendation"] = "Contraseña incorrecta (Error 1045): MariaDB 10 rechazó al usuario '" . DB_USER . "' en " . DB_HOST . ":" . DB_PORT . ". Abre $currentDir/config.php y actualiza la variable DB_PASS con la contraseña exacta de MariaDB 10. Si la olvidaste, puedes cambiarla en DSM -> MariaDB 10.";
        } elseif (stripos($effectiveError, 'Connection refused') !== false) {
            $report["recommendation"] = "Conexión TCP rechazada (Error 2002): Verifica en DSM -> Centro de Paquetes que 'MariaDB 10' esté 'En ejecución'.";
        } elseif (stripos($effectiveError, 'open_basedir') !== false || stripos($effectiveError, 'No such file') !== false) {
            $report["recommendation"] = "Socket inaccesible por directiva open_basedir: En tu perfil PHP 7.3 de Web Station, la directiva open_basedir bloquea el socket UNIX. La solución es conectar por TCP usando DB_HOST='127.0.0.1' y DB_PORT=3307 en config.php.";
        } else {
            $report["recommendation"] = "Error al autenticar con MariaDB: $effectiveError. Verifica usuario y clave en $currentDir/config.php.";
        }
    } else {
        $tableStatus = count($missingTables) === 0 ? "Las 8 tablas del sistema están 100% verificadas." : "Faltan tablas: " . implode(', ', $missingTables) . ". Pulsa 'Verificar / Reparar' en la app.";
        $report["recommendation"] = "¡Conexión perfecta! MariaDB 10 y Web Station en $detectedVol están sincronizados. $tableStatus";
    }

    echo json_encode($report);
    exit;
}

// 1.1. REPARACIÓN / INICIALIZACIÓN AUTOMÁTICA DE BASE DE DATOS Y TABLAS
if (matchRoute($action, $uri, 'repair_db') || matchRoute($action, $uri, 'repair') || matchRoute($action, $uri, 'init_schema')) {
    try {
        $db = getDB();
        autoBootstrapDatabase($db);

        $tablesStmt = $db->query("SHOW TABLES");
        $tables = $tablesStmt ? $tablesStmt->fetchAll(PDO::FETCH_COLUMN) : [];
        $expected = ['users', 'family_groups', 'posts', 'media', 'media_chunks', 'comments', 'reactions', 'stories'];
        $missing = array_values(array_diff($expected, $tables));

        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => "OK",
            "message" => "Tablas en MariaDB 10 verificadas y sincronizadas exitosamente: " . count($tables) . "/8 (" . implode(', ', $tables) . ")",
            "database" => DB_NAME,
            "tables" => $tables,
            "expected_count" => 8,
            "missing_tables" => $missing,
            "time" => date("Y-m-d H:i:s")
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["status" => "ERROR", "error" => $e->getMessage()]);
    }
    exit;
}

// 1.2. STREAMING / PROXY DE ARCHIVOS MULTIMEDIA DEL NAS (Soporta Range para ExoPlayer)
if ($method === 'GET' && (matchRoute($action, $uri, 'media_proxy') || matchRoute($action, $uri, 'stream') || str_ends_with($uri, '/media_proxy'))) {
    $filePath = $_GET['path'] ?? '';
    // Si la ruta es relativa dentro del servidor web
    if (!empty($filePath) && !str_starts_with($filePath, '/')) {
        $filePath = __DIR__ . '/' . ltrim($filePath, '/');
    }
    streamMediaFile($filePath);
    exit;
}

// 2. OBTENER USUARIOS
if ($method === 'GET' && (matchRoute($action, $uri, 'users') || str_ends_with($uri, '/users'))) {
    try {
        $db = getDB();
        $stmt = $db->query("SELECT id, display_name, role, avatar_emoji, avatar_color_hex, bio, pin_hash FROM users ORDER BY id ASC");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode($stmt->fetchAll());
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 2.1. LOGIN DE USUARIOS
if ($method === 'POST' && (matchRoute($action, $uri, 'auth/login') || str_ends_with($uri, '/auth/login'))) {
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

// 2.2. REGISTRO REAL DE USUARIOS EN MARIADB 10
if ($method === 'POST' && (matchRoute($action, $uri, 'auth/register') || str_ends_with($uri, '/auth/register'))) {
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
                "id" => $newId,
                "username" => $username,
                "display_name" => $displayName,
                "role" => $role,
                "avatar_emoji" => $avatarEmoji,
                "avatar_color_hex" => $avatarColor
            ]
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3.1. OBTENER GRUPOS
if ($method === 'GET' && (matchRoute($action, $uri, 'groups') || str_ends_with($uri, '/groups'))) {
    try {
        $db = getDB();
        $stmt = $db->query("
            SELECT g.*, 
                   (SELECT COUNT(*) FROM posts p WHERE p.group_id = g.id) as posts_count
            FROM family_groups g
            ORDER BY g.updated_at DESC
        ");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["groups" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 3.2. OBTENER PUBLICACIONES DE UN GRUPO ESPECÍFICO (COLOCADO ANTES DEL FEED GENÉRICO)
if ($method === 'GET' && (matchRoute($action, $uri, 'group/posts') || str_ends_with($uri, '/group/posts'))) {
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
                   (SELECT GROUP_CONCAT(c.file_path ORDER BY c.chunk_index ASC SEPARATOR '|||') FROM media_chunks c WHERE c.post_id = p.id) as chunks_paths,
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

// 3.3. OBTENER SHORTS DE VÍDEO
if ($method === 'GET' && (matchRoute($action, $uri, 'shorts') || str_ends_with($uri, '/shorts'))) {
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
                   (SELECT GROUP_CONCAT(c.file_path ORDER BY c.chunk_index ASC SEPARATOR '|||') FROM media_chunks c WHERE c.post_id = p.id) as chunks_paths,
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

// 3.4. OBTENER FEED COMPLETO (MURO)
if ($method === 'GET' && (matchRoute($action, $uri, 'feed') || matchRoute($action, $uri, 'posts') || str_ends_with($uri, '/feed') || str_ends_with($uri, '/posts'))) {
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
                   (SELECT GROUP_CONCAT(c.file_path ORDER BY c.chunk_index ASC SEPARATOR '|||') FROM media_chunks c WHERE c.post_id = p.id) as chunks_paths,
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

// 3.5. SUBIDA DE FRAGMENTOS DE VÍDEO
if ($method === 'POST' && (matchRoute($action, $uri, 'video/upload_chunk') || str_ends_with($uri, '/video/upload_chunk'))) {
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

    if ($_FILES[$fileKey]['error'] !== UPLOAD_ERR_OK) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "Error al recibir fragmento #{$chunkIndex}: código " . $_FILES[$fileKey]['error']]);
        exit;
    }

    $chunkDir = __DIR__ . '/uploads/chunks/' . $sessionId;
    if (!is_dir($chunkDir)) @mkdir($chunkDir, 0777, true);

    $chunkName = sprintf("chunk_%04d.mp4", $chunkIndex);
    $targetPath = $chunkDir . '/' . $chunkName;

    move_uploaded_file($_FILES[$fileKey]['tmp_name'], $targetPath);

    header("Content-Type: application/json; charset=UTF-8");
    echo json_encode([
        "status" => "OK",
        "session_id" => $sessionId,
        "chunk_index" => $chunkIndex,
        "received" => true
    ]);
    exit;
}

// 3.6. FINALIZAR PUBLICACIÓN CON VÍDEO FRAGMENTADO
if ($method === 'POST' && (matchRoute($action, $uri, 'video/finalize_chunks') || str_ends_with($uri, '/video/finalize_chunks'))) {
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
        echo json_encode(["error" => "No se encontró el directorio de fragmentos para la sesión {$sessionId}"]);
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
        if (!$hasThumb) {
            $thumbRel = $firstChunkRel;
        }

        $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label, is_chunked) VALUES (?, 'VIDEO', ?, ?, ?, 1)");
        $stmtMedia->execute([$postId, $firstChunkRel, $thumbRel, $durationLabel ?: null]);

        $stmtChunk = $db->prepare("INSERT INTO media_chunks (post_id, chunk_index, total_chunks, file_path) VALUES (?, ?, ?, ?)");
        for ($i = 0; $i < $totalChunks; $i++) {
            $relPath = sprintf("uploads/chunks/%s/chunk_%04d.mp4", $sessionId, $i);
            $stmtChunk->execute([$postId, $i, $totalChunks, $relPath]);
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "success" => true,
            "post_id" => $postId,
            "total_chunks" => $totalChunks,
            "is_chunked" => true
        ]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 4. CREAR PUBLICACIÓN CON ARCHIVO DIRECTO
if ($method === 'POST' && (matchRoute($action, $uri, 'post') || matchRoute($action, $uri, 'posts') || str_ends_with($uri, '/post') || str_ends_with($uri, '/posts'))) {
    $userId = (int)($_POST['user_id'] ?? 1);
    $text = trim($_POST['content_text'] ?? '');
    $feeling = $_POST['feeling'] ?? null;
    $location = $_POST['location'] ?? null;
    $durationLabel = trim($_POST['duration_label'] ?? '');
    $isShort = (int)($_POST['is_short'] ?? 0);
    $groupId = !empty($_POST['group_id']) ? (int)$_POST['group_id'] : null;

    try {
        $db = getDB();
        $db->beginTransaction();

        $stmt = $db->prepare("INSERT INTO posts (user_id, group_id, content_text, feeling, location, is_short) VALUES (?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $groupId, $text, $feeling, $location, $isShort]);
        $postId = $db->lastInsertId();

        if (isset($_FILES['photo']) && $_FILES['photo']['error'] === UPLOAD_ERR_OK) {
            $uploadDir = __DIR__ . '/uploads';
            if (!is_dir($uploadDir . '/originals')) @mkdir($uploadDir . '/originals', 0777, true);
            if (!is_dir($uploadDir . '/thumbs')) @mkdir($uploadDir . '/thumbs', 0777, true);

            $ext = strtolower(pathinfo($_FILES['photo']['name'], PATHINFO_EXTENSION));
            $uuid = uniqid('media_', true);
            $relOrig = "uploads/originals/{$uuid}.{$ext}";
            $relThumb = "uploads/thumbs/{$uuid}.webp";

            move_uploaded_file($_FILES['photo']['tmp_name'], __DIR__ . '/' . $relOrig);

            $isVideo = in_array($ext, ['mp4', 'mov', 'mkv', 'avi', '3gp', 'webm', 'm4v']);
            if (isset($_FILES['thumbnail']) && $_FILES['thumbnail']['error'] === UPLOAD_ERR_OK) {
                move_uploaded_file($_FILES['thumbnail']['tmp_name'], __DIR__ . '/' . $relThumb);
            } else if (!$isVideo) {
                $relThumb = $relOrig;
            } else {
                $relThumb = $relOrig;
            }

            $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path, duration_label) VALUES (?, ?, ?, ?, ?)");
            $stmtMedia->execute([$postId, $isVideo ? 'VIDEO' : 'IMAGE', $relOrig, $relThumb, $durationLabel ?: null]);
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "post_id" => (int)$postId]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 5. REACCIONES
if ($method === 'POST' && (matchRoute($action, $uri, 'reaction') || str_ends_with($uri, '/reaction'))) {
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
            header("Content-Type: application/json; charset=UTF-8");
            echo json_encode(["action" => "removed"]);
        } else {
            $ins = $db->prepare("INSERT INTO reactions (post_id, user_id, reaction_type) VALUES (?, ?, ?)");
            $ins->execute([$postId, $userId, $rxType]);
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

// 6. COMENTARIOS
if ($method === 'POST' && (matchRoute($action, $uri, 'comment') || str_ends_with($uri, '/comment'))) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $postId = (int)($input['post_id'] ?? 0);
    $userId = (int)($input['user_id'] ?? 0);
    $text = trim($input['comment_text'] ?? '');

    try {
        $db = getDB();
        $stmt = $db->prepare("INSERT INTO comments (post_id, user_id, comment_text) VALUES (?, ?, ?)");
        $stmt->execute([$postId, $userId, $text]);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["success" => true, "comment_id" => $db->lastInsertId()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// 7. OBTENER COMENTARIOS DE UN POST
if ($method === 'GET' && (matchRoute($action, $uri, 'comments') || str_ends_with($uri, '/comments'))) {
    $postId = (int)($_GET['post_id'] ?? 0);
    try {
        $db = getDB();
        $stmt = $db->prepare("
            SELECT c.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex
            FROM comments c
            JOIN users u ON c.user_id = u.id
            WHERE c.post_id = ?
            ORDER BY c.created_at ASC
        ");
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

// 8. HISTORIAS (STORIES)
if ($method === 'GET' && (matchRoute($action, $uri, 'stories') || str_ends_with($uri, '/stories'))) {
    try {
        $db = getDB();
        $stmt = $db->query("
            SELECT s.*, u.display_name, u.role, u.avatar_emoji, u.avatar_color_hex
            FROM stories s
            JOIN users u ON s.user_id = u.id
            ORDER BY s.created_at DESC
            LIMIT 30
        ");
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["stories" => $stmt->fetchAll()]);
    } catch (Exception $e) {
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

// Función auxiliar para escanear recursivamente todas las subcarpetas omitiendo carpetas del sistema (@eaDir, #recycle, etc.)
function collectFolderMediaRecursive($dir) {
    $results = [];
    if (!@is_dir($dir)) return $results;

    $imgExts = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp', 'heic'];
    $vidExts = ['mp4', 'mov', 'mkv', 'avi', '3gp', 'webm', 'm4v'];

    try {
        $iterator = new RecursiveIteratorIterator(
            new RecursiveDirectoryIterator($dir, RecursiveDirectoryIterator::SKIP_DOTS),
            RecursiveIteratorIterator::SELF_FIRST
        );
        foreach ($iterator as $item) {
            $name = $item->getFilename();
            if ($name === '@eaDir' || $name === '#recycle' || strpos($name, '.') === 0) continue;
            $path = $item->getPathname();
            if (strpos($path, '/@eaDir') !== false || strpos($path, '/#recycle') !== false) continue;
            if ($item->isFile()) {
                $ext = strtolower($item->getExtension());
                if (in_array($ext, $imgExts)) {
                    $subDir = dirname(substr($path, strlen($dir) + 1));
                    $results[] = [
                        "path" => $path,
                        "type" => "IMAGE",
                        "name" => pathinfo($item->getFilename(), PATHINFO_FILENAME),
                        "rel_subfolder" => ($subDir !== '.' && $subDir !== '') ? str_replace('\\', '/', $subDir) : ''
                    ];
                } else if (in_array($ext, $vidExts)) {
                    $subDir = dirname(substr($path, strlen($dir) + 1));
                    $results[] = [
                        "path" => $path,
                        "type" => "VIDEO",
                        "name" => pathinfo($item->getFilename(), PATHINFO_FILENAME),
                        "rel_subfolder" => ($subDir !== '.' && $subDir !== '') ? str_replace('\\', '/', $subDir) : ''
                    ];
                }
            }
        }
    } catch (Exception $e) {
        $scan = function($curDir) use (&$scan, &$results, $imgExts, $vidExts, $dir) {
            $files = @scandir($curDir);
            if ($files === false) return;
            foreach ($files as $f) {
                if ($f === '.' || $f === '..' || $f === '@eaDir' || $f === '#recycle' || strpos($f, '.') === 0) continue;
                $full = $curDir . '/' . $f;
                if (@is_dir($full)) {
                    $scan($full);
                } elseif (@is_file($full)) {
                    $ext = strtolower(pathinfo($f, PATHINFO_EXTENSION));
                    $subDir = dirname(substr($full, strlen($dir) + 1));
                    if (in_array($ext, $imgExts)) {
                        $results[] = [
                            "path" => $full, 
                            "type" => "IMAGE", 
                            "name" => pathinfo($f, PATHINFO_FILENAME),
                            "rel_subfolder" => ($subDir !== '.' && $subDir !== '') ? str_replace('\\', '/', $subDir) : ''
                        ];
                    } elseif (in_array($ext, $vidExts)) {
                        $results[] = [
                            "path" => $full, 
                            "type" => "VIDEO", 
                            "name" => pathinfo($f, PATHINFO_FILENAME),
                            "rel_subfolder" => ($subDir !== '.' && $subDir !== '') ? str_replace('\\', '/', $subDir) : ''
                        ];
                    }
                }
            }
        };
        $scan($dir);
    }
    return $results;
}

// 9. ESCANEAR CARPETAS DEL NAS (CON SOPORTE RECURSIVO DE SUBCARPETAS)
if (($method === 'GET' || $method === 'POST') && (matchRoute($action, $uri, 'admin/scan_folders') || str_ends_with($uri, '/admin/scan_folders'))) {
    $basePath = trim($_GET['base_path'] ?? $_POST['base_path'] ?? '/volume1/photo');
    if (empty($basePath)) $basePath = '/volume1/photo';

    if (!@is_dir($basePath)) {
        http_response_code(404);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "La ruta '{$basePath}' no existe o no tiene permisos de lectura"]);
        exit;
    }

    try {
        $db = getDB();
        $existingPaths = [];
        try {
            $stmtExisting = $db->query("SELECT folder_path FROM family_groups");
            $existingPaths = $stmtExisting->fetchAll(PDO::FETCH_COLUMN) ?: [];
        } catch (Exception $ex) {}

        $entries = @scandir($basePath);
        $folders = [];

        if ($entries !== false) {
            foreach ($entries as $entry) {
                if ($entry === '.' || $entry === '..' || $entry === '@eaDir' || $entry === '#recycle') continue;
                $fullPath = rtrim($basePath, '/') . '/' . $entry;
                if (@is_dir($fullPath)) {
                    $mediaList = collectFolderMediaRecursive($fullPath);
                    $photoCount = 0;
                    $videoCount = 0;
                    foreach ($mediaList as $mItem) {
                        if ($mItem['type'] === 'IMAGE') $photoCount++;
                        else if ($mItem['type'] === 'VIDEO') $videoCount++;
                    }

                    $isImported = in_array($fullPath, $existingPaths);
                    $folders[] = [
                        "name" => $entry,
                        "path" => $fullPath,
                        "photos_count" => $photoCount,
                        "videos_count" => $videoCount,
                        "media_count" => ($photoCount + $videoCount),
                        "has_subfolders" => true,
                        "already_imported" => $isImported
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

// 10. IMPORTAR CARPETAS COMO GRUPOS (RECURSIVO EN SUBCARPETAS Y PUBLICACIÓN PROGRESIVA EN SEGUNDO PLANO)
if ($method === 'POST' && (matchRoute($action, $uri, 'admin/import_groups') || str_ends_with($uri, '/admin/import_groups'))) {
    $input = json_decode(file_get_contents('php://input'), true) ?? $_POST;
    $folderPaths = $input['folder_paths'] ?? [];
    if (is_string($folderPaths)) {
        $folderPaths = explode(',', $folderPaths);
    }
    $adminUserId = (int)($input['admin_user_id'] ?? 1);
    $batchOffset = (int)($input['batch_offset'] ?? 0);
    $batchLimit = (int)($input['batch_limit'] ?? 50);
    $isBatch = !empty($input['is_batch']) || isset($input['batch_offset']);

    if (empty($folderPaths) || !is_array($folderPaths)) {
        http_response_code(400);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => "No se recibieron carpetas para importar"]);
        exit;
    }

    try {
        $db = getDB();
        $db->beginTransaction();
        $importedGroups = 0;
        $importedPosts = 0;
        $totalFiles = 0;
        $hasMore = false;
        $nextOffset = 0;

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
                $groupStmt->execute([$groupName, $fPath, "Grupo familiar sincronizado con subcarpetas desde {$fPath}"]);
                $groupId = (int)$db->lastInsertId();
            }

            // Recolectar todos los archivos de fotos y vídeos de la carpeta y todas sus subcarpetas
            $allMedia = collectFolderMediaRecursive($fPath);
            $totalInFolder = count($allMedia);
            $totalFiles += $totalInFolder;

            // Procesar por lote progresivo o completo
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
                if (!empty($mediaInfo['rel_subfolder'])) {
                    $caption .= " (" . $mediaInfo['rel_subfolder'] . ")";
                }

                $stmtPost = $db->prepare("INSERT INTO posts (user_id, group_id, content_text, is_short) VALUES (?, ?, ?, 0)");
                $stmtPost->execute([$adminUserId, $groupId, $caption]);
                $newPostId = (int)$db->lastInsertId();

                $stmtMedia = $db->prepare("INSERT INTO media (post_id, media_type, file_path, thumb_path) VALUES (?, ?, ?, ?)");
                $stmtMedia->execute([
                    $newPostId,
                    $isVid ? 'VIDEO' : 'IMAGE',
                    $proxyUrl,
                    $proxyUrl
                ]);

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

            // Actualizar contador total de publicaciones del grupo
            $db->prepare("UPDATE family_groups SET posts_count = (SELECT COUNT(*) FROM posts WHERE group_id = ?) WHERE id = ?")->execute([$groupId, $groupId]);

            $importedGroups++;
        }

        $db->commit();
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode([
            "status" => "OK",
            "imported_groups" => $importedGroups,
            "imported_posts" => $importedPosts,
            "batch_offset" => $batchOffset,
            "batch_imported" => $importedPosts,
            "total_files" => $totalFiles,
            "has_more" => $hasMore,
            "next_offset" => $nextOffset
        ]);
    } catch (Exception $e) {
        if (isset($db) && $db->inTransaction()) $db->rollBack();
        http_response_code(500);
        header("Content-Type: application/json; charset=UTF-8");
        echo json_encode(["error" => $e->getMessage()]);
    }
    exit;
}

header("Content-Type: application/json; charset=UTF-8");
echo json_encode(["message" => "Endpoint no encontrado"]);
