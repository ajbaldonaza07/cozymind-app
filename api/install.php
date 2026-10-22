<?php
// One-time setup for Laragon: creates the "cozymind" database and its tables.
// Open http://localhost/cozymind-api/install.php once. Safe to run again (it won't delete data).
header('Content-Type: text/plain; charset=utf-8');
$config = require __DIR__ . '/config.php';
try {
    $pdo = new PDO("mysql:host={$config['db_host']};charset=utf8mb4", $config['db_user'], $config['db_pass'],
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec("CREATE DATABASE IF NOT EXISTS `{$config['db_name']}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    $pdo->exec("USE `{$config['db_name']}`");
    $sql = file_get_contents(__DIR__ . '/schema.sql');
    foreach (array_filter(array_map('trim', explode(';', preg_replace('/^--.*$/m', '', $sql)))) as $statement) {
        $pdo->exec($statement);
    }
    $tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
    echo "OK: database '{$config['db_name']}' is ready.\nTables: " . implode(', ', $tables) . "\n";
} catch (Throwable $e) {
    http_response_code(500);
    echo "FAILED: " . $e->getMessage() . "\nIs MySQL started in Laragon (Start All)?\n";
}
