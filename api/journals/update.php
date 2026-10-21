<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'PUT', 'PATCH');
$userId = require_user_id();

$body = read_json_body();
$id   = get_id($body);

$pdo = db();
$check = $pdo->prepare('SELECT title, body FROM journals WHERE id = ? AND user_id = ?');
$check->execute([$id, $userId]);
$current = $check->fetch();
if (!$current) fail('Journal not found.', 404);

// Fields that are not sent keep their current value.
$title = array_key_exists('title', $body) ? field($body, 'title', 150) : $current['title'];
$text  = array_key_exists('body', $body)  ? field($body, 'body', 20000) : $current['body'];
if ($text === '') fail('Write a few words before saving.', 422);
if ($title === '') $title = 'A quiet moment';

$pdo->prepare('UPDATE journals SET title = ?, body = ?, updated_at = ? WHERE id = ? AND user_id = ?')
    ->execute([$title, $text, now_utc(), $id, $userId]);

$stmt = $pdo->prepare('SELECT id, title, body, created_at, updated_at FROM journals WHERE id = ?');
$stmt->execute([$id]);
$j = $stmt->fetch();
$j['id'] = (int) $j['id'];
respond(['journal' => $j]);
