<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();
$id = get_id();   // ?id=1

$stmt = db()->prepare(
    'SELECT id, sleep_date, bed_time, wake_time, quality, notes, created_at FROM sleep_logs WHERE id = ? AND user_id = ?'
);
$stmt->execute([$id, $userId]);
$log = $stmt->fetch();
if (!$log) fail('Sleep log not found.', 404);

$log['id']      = (int) $log['id'];
$log['quality'] = (int) $log['quality'];
respond(['sleep_log' => $log]);
