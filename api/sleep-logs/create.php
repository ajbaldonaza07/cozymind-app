<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');
$userId = require_user_id();

$body      = read_json_body();
$sleepDate = valid_date($body['sleep_date'] ?? null, 'sleep_date');
$bedTime   = valid_time($body['bed_time'] ?? null, 'bed_time');
$wakeTime  = valid_time($body['wake_time'] ?? null, 'wake_time');
$quality   = int_in_range($body['quality'] ?? null, 'quality', 1, 5);
$notes     = field($body, 'notes', 255);

$pdo = db();
$now = now_utc();
$pdo->prepare(
    'INSERT INTO sleep_logs (user_id, sleep_date, bed_time, wake_time, quality, notes, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)'
)->execute([$userId, $sleepDate, $bedTime, $wakeTime, $quality, $notes, $now]);

respond(['ok' => true, 'sleep_log' => [
    'id' => (int) $pdo->lastInsertId(), 'sleep_date' => $sleepDate, 'bed_time' => $bedTime,
    'wake_time' => $wakeTime, 'quality' => $quality, 'notes' => $notes, 'created_at' => $now,
]], 201);
