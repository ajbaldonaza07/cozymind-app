<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');
$userId = require_user_id();

$body  = read_json_body();
$title = field($body, 'title', 150);
$text  = field($body, 'body', 20000);
if ($text === '') fail('Write a few words before saving.', 422);
if ($title === '') $title = 'A quiet moment';

$pdo = db();
$now = now_utc();
$pdo->prepare('INSERT INTO journals (user_id, title, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?)')
    ->execute([$userId, $title, $text, $now, $now]);

respond(['ok' => true, 'journal' => [
    'id' => (int) $pdo->lastInsertId(), 'title' => $title, 'body' => $text, 'created_at' => $now,
]], 201);
