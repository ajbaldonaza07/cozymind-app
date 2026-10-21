<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'PUT', 'PATCH');
$userId = require_user_id();

$body = read_json_body();
$id = get_id($body);
$severity = isset($body['severity']) ? (int) $body['severity'] : 0;
if ($severity < 1 || $severity > 5) fail('Severity must be between 1 and 5.', 422);

$pdo = db();
$check = $pdo->prepare('SELECT id FROM mood_trackers WHERE id = ? AND user_id = ?');
$check->execute([$id, $userId]);
if (!$check->fetch()) fail('Mood not found.', 404);

$pdo->prepare('UPDATE mood_trackers SET severity = ? WHERE id = ? AND user_id = ?')
    ->execute([$severity, $id, $userId]);

$stmt = $pdo->prepare('SELECT id, severity, datetime_mood FROM mood_trackers WHERE id = ?');
$stmt->execute([$id]);
$m = $stmt->fetch();
respond(['mood' => ['id' => (int) $m['id'], 'severity' => (int) $m['severity'], 'datetime_mood' => $m['datetime_mood']]]);
