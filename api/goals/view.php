<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();
$id = get_id();   // ?id=1

$stmt = db()->prepare(
    'SELECT id, title, description, frequency, start_date, end_date, status, created_at, updated_at
     FROM goals WHERE id = ? AND user_id = ?'
);
$stmt->execute([$id, $userId]);
$goal = $stmt->fetch();
if (!$goal) fail('Goal not found.', 404);

$goal['id'] = (int) $goal['id'];
respond(['goal' => $goal]);
