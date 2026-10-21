<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();
$id = get_id();   // ?id=1

$stmt = db()->prepare('SELECT id, severity, datetime_mood FROM mood_trackers WHERE id = ? AND user_id = ?');
$stmt->execute([$id, $userId]);
$mood = $stmt->fetch();
if (!$mood) fail('Mood not found.', 404);

respond(['mood' => [
    'id' => (int) $mood['id'], 'severity' => (int) $mood['severity'], 'datetime_mood' => $mood['datetime_mood'],
]]);
