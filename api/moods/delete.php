<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'DELETE');
$userId = require_user_id();
$id = get_id(read_json_body());

$stmt = db()->prepare('DELETE FROM mood_trackers WHERE id = ? AND user_id = ?');
$stmt->execute([$id, $userId]);
if ($stmt->rowCount() === 0) fail('Mood not found.', 404);

respond(['ok' => true, 'deleted_id' => $id]);
