<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'PUT', 'PATCH');
$userId = require_user_id();

$body = read_json_body();
$id   = get_id($body);

$fields = ['sleep_date', 'bed_time', 'wake_time', 'quality', 'notes'];
if (!array_intersect_key($body, array_flip($fields))) fail('Nothing to update.', 422);

$pdo = db();
$check = $pdo->prepare('SELECT sleep_date, bed_time, wake_time, quality, notes FROM sleep_logs WHERE id = ? AND user_id = ?');
$check->execute([$id, $userId]);
$cur = $check->fetch();
if (!$cur) fail('Sleep log not found.', 404);

// Fields that are not sent keep their current value.
$sleepDate = array_key_exists('sleep_date', $body) ? valid_date($body['sleep_date'], 'sleep_date') : $cur['sleep_date'];
$bedTime   = array_key_exists('bed_time', $body)   ? valid_time($body['bed_time'], 'bed_time')     : $cur['bed_time'];
$wakeTime  = array_key_exists('wake_time', $body)  ? valid_time($body['wake_time'], 'wake_time')   : $cur['wake_time'];
$quality   = array_key_exists('quality', $body)    ? int_in_range($body['quality'], 'quality', 1, 5) : (int) $cur['quality'];
$notes     = array_key_exists('notes', $body)      ? field($body, 'notes', 255)                    : $cur['notes'];

$pdo->prepare(
    'UPDATE sleep_logs SET sleep_date = ?, bed_time = ?, wake_time = ?, quality = ?, notes = ? WHERE id = ? AND user_id = ?'
)->execute([$sleepDate, $bedTime, $wakeTime, $quality, $notes, $id, $userId]);

$stmt = $pdo->prepare('SELECT id, sleep_date, bed_time, wake_time, quality, notes, created_at FROM sleep_logs WHERE id = ?');
$stmt->execute([$id]);
$log = $stmt->fetch();
$log['id']      = (int) $log['id'];
$log['quality'] = (int) $log['quality'];
respond(['sleep_log' => $log]);
