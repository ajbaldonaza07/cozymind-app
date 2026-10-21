<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();

$stmt = db()->prepare(
    'SELECT id, title, description, frequency, start_date, end_date, status, created_at, updated_at
     FROM goals WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 100'
);
$stmt->execute([$userId]);

respond(['goals' => array_map(function ($g) {
    $g['id'] = (int) $g['id'];
    return $g;
}, $stmt->fetchAll())]);
