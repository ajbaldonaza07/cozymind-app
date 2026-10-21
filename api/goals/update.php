<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'PUT', 'PATCH');
$userId = require_user_id();

$body = read_json_body();
$id   = get_id($body);

$fields = ['title', 'description', 'frequency', 'start_date', 'end_date', 'status'];
if (!array_intersect_key($body, array_flip($fields))) fail('Nothing to update.', 422);

$pdo = db();
$check = $pdo->prepare('SELECT title, description, frequency, start_date, end_date, status FROM goals WHERE id = ? AND user_id = ?');
$check->execute([$id, $userId]);
$cur = $check->fetch();
if (!$cur) fail('Goal not found.', 404);

// Fields that are not sent keep their current value.
$title = array_key_exists('title', $body) ? field($body, 'title', 150) : $cur['title'];
if ($title === '') fail('Enter a title for your goal.', 422);
$description = array_key_exists('description', $body) ? field($body, 'description', 5000) : $cur['description'];
$frequency   = array_key_exists('frequency', $body) ? enum_field($body['frequency'], 'frequency', GOAL_FREQUENCIES) : $cur['frequency'];
$status      = array_key_exists('status', $body) ? enum_field($body['status'], 'status', GOAL_STATUSES) : $cur['status'];
$start       = array_key_exists('start_date', $body) ? valid_date($body['start_date'], 'start_date') : $cur['start_date'];
$end         = $cur['end_date'];
if (array_key_exists('end_date', $body)) {
    // null or "" clears the end date
    $end = ($body['end_date'] === null || $body['end_date'] === '') ? null : valid_date($body['end_date'], 'end_date');
}
if ($end !== null && $end < $start) fail('end_date cannot be before start_date.', 422);

$pdo->prepare(
    'UPDATE goals SET title = ?, description = ?, frequency = ?, start_date = ?, end_date = ?, status = ?, updated_at = ?
     WHERE id = ? AND user_id = ?'
)->execute([$title, $description, $frequency, $start, $end, $status, now_utc(), $id, $userId]);

$stmt = $pdo->prepare(
    'SELECT id, title, description, frequency, start_date, end_date, status, created_at, updated_at FROM goals WHERE id = ?'
);
$stmt->execute([$id]);
$goal = $stmt->fetch();
$goal['id'] = (int) $goal['id'];
respond(['goal' => $goal]);
