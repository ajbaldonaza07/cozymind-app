<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();

$stmt = db()->prepare(
    'SELECT id, title, body, created_at, updated_at FROM journals
     WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 50'
);
$stmt->execute([$userId]);

respond(['journals' => array_map(function ($j) {
    $j['id'] = (int) $j['id'];
    return $j;
}, $stmt->fetchAll())]);
