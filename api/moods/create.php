<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');
$userId = require_user_id();

$body = read_json_body();
$severity = isset($body['severity']) ? (int) $body['severity'] : 0;
if ($severity < 1 || $severity > 5) fail('Severity must be between 1 and 5.', 422);

$pdo = db();
$now = now_utc();
$pdo->prepare('INSERT INTO mood_trackers (user_id, datetime_mood, severity, created_at) VALUES (?, ?, ?, ?)')
    ->execute([$userId, $now, $severity, $now]);

respond(['ok' => true, 'mood' => [
    'id' => (int) $pdo->lastInsertId(), 'severity' => $severity, 'datetime_mood' => $now,
]], 201);
