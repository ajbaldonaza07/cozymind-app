<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');
$userId = require_user_id();

$body  = read_json_body();
$title = field($body, 'title', 150);
if ($title === '') fail('Enter a title for your goal.', 422);
$description = field($body, 'description', 5000);
$frequency   = enum_field($body['frequency'] ?? 'daily', 'frequency', GOAL_FREQUENCIES);
$status      = enum_field($body['status'] ?? 'active', 'status', GOAL_STATUSES);

$start = (isset($body['start_date']) && $body['start_date'] !== '')
    ? valid_date($body['start_date'], 'start_date') : gmdate('Y-m-d');
$end = (isset($body['end_date']) && $body['end_date'] !== '')
    ? valid_date($body['end_date'], 'end_date') : null;
if ($end !== null && $end < $start) fail('end_date cannot be before start_date.', 422);

$pdo = db();
$now = now_utc();
$pdo->prepare(
    'INSERT INTO goals (user_id, title, description, frequency, start_date, end_date, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
)->execute([$userId, $title, $description, $frequency, $start, $end, $status, $now, $now]);

respond(['ok' => true, 'goal' => [
    'id' => (int) $pdo->lastInsertId(), 'title' => $title, 'description' => $description,
    'frequency' => $frequency, 'start_date' => $start, 'end_date' => $end, 'status' => $status,
    'created_at' => $now, 'updated_at' => $now,
]], 201);
