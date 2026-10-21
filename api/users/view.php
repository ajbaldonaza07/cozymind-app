<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$me = require_user_id();

$id = get_id();   // ?id=1
if ($id !== $me && !is_admin($me)) fail('You can only view your own account.', 403);

$stmt = db()->prepare(
    'SELECT id, name, username, email, phone_number, is_admin, created_at, updated_at FROM users WHERE id = ?'
);
$stmt->execute([$id]);
$user = $stmt->fetch();
if (!$user) fail('User not found.', 404);

$user['id'] = (int) $user['id'];
$user['is_admin'] = (int) $user['is_admin'];
respond(['user' => $user]);
