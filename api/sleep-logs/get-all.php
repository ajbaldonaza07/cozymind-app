<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();

$stmt = db()->prepare(
    'SELECT id, sleep_date, bed_time, wake_time, quality, notes, created_at
     FROM sleep_logs WHERE user_id = ? ORDER BY sleep_date DESC, id DESC LIMIT 100'
);
$stmt->execute([$userId]);

respond(['sleep_logs' => array_map(function ($s) {
    $s['id']      = (int) $s['id'];
    $s['quality'] = (int) $s['quality'];
    return $s;
}, $stmt->fetchAll())]);
