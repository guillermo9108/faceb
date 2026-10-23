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

function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];
        $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
    }
    return $pdo;
}
