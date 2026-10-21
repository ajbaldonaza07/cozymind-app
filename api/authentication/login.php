<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');

$body     = read_json_body();
$identity = field($body, 'identity', 190);   // email OR username
$password = isset($body['password']) && is_string($body['password']) ? $body['password'] : '';
if ($identity === '' || $password === '') fail('Enter your email/username and password.', 422);

$stmt = db()->prepare('SELECT * FROM users WHERE email = ? OR username = ? LIMIT 1');
$stmt->execute([strtolower($identity), $identity]);
$user = $stmt->fetch();
if (!$user || !password_verify($password, $user['password'])) {
    fail('Email/username or password does not match.', 401);
}

session_regenerate_id(true);
$_SESSION['user_id'] = (int) $user['id'];
respond(['user' => public_user($user)]);
