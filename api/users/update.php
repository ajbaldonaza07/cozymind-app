<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'PUT', 'PATCH');
$me = require_user_id();

$body = read_json_body();
$id   = get_id($body);
$admin = is_admin($me);
if ($id !== $me && !$admin) fail('You can only update your own account.', 403);

$pdo = db();
$exists = $pdo->prepare('SELECT id FROM users WHERE id = ?');
$exists->execute([$id]);
if (!$exists->fetch()) fail('User not found.', 404);

// Only the fields that were sent are changed.
$sets = [];
$vals = [];

if (array_key_exists('name', $body)) {
    $name = field($body, 'name', 100);
    if ($name === '') fail('Enter your name.', 422);
    $sets[] = 'name = ?'; $vals[] = $name;
}
if (array_key_exists('username', $body)) {
    $username = field($body, 'username', 50);
    if (!preg_match('/^[A-Za-z0-9_.]{3,50}$/', $username))
        fail('Username must be 3–50 letters, numbers, dots or underscores.', 422);
    $sets[] = 'username = ?'; $vals[] = $username;
}
if (array_key_exists('email', $body)) {
    $email = strtolower(field($body, 'email', 190));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Enter a valid email address.', 422);
    $sets[] = 'email = ?'; $vals[] = $email;
}
if (array_key_exists('phone_number', $body)) {
    $phone = field($body, 'phone_number', 30);
    if (strlen(preg_replace('/\D/', '', $phone)) < 7) fail('Enter a valid phone number.', 422);
    $sets[] = 'phone_number = ?'; $vals[] = $phone;
}
if (array_key_exists('password', $body)) {
    $password = is_string($body['password']) ? $body['password'] : '';
    if (strlen($password) < 6)  fail('Use at least 6 characters.', 422);
    if (strlen($password) > 72) fail('Password is too long.', 422);
    $sets[] = 'password = ?'; $vals[] = password_hash($password, PASSWORD_DEFAULT);
}
if ($admin && array_key_exists('is_admin', $body)) {
    $sets[] = 'is_admin = ?'; $vals[] = !empty($body['is_admin']) ? 1 : 0;
}

if (!$sets) fail('Nothing to update.', 422);

// Make sure the new username/email is not taken by someone else.
if (isset($username) || isset($email)) {
    $dup = $pdo->prepare('SELECT username, email FROM users WHERE id <> ? AND (username = ? OR email = ?) LIMIT 1');
    $dup->execute([$id, $username ?? '', $email ?? '']);
    if ($d = $dup->fetch()) {
        fail(isset($email) && strcasecmp($d['email'], $email) === 0
            ? 'That email already has an account.' : 'That username is taken.', 409);
    }
}

$sets[] = 'updated_at = ?'; $vals[] = now_utc();
$vals[] = $id;
$pdo->prepare('UPDATE users SET ' . implode(', ', $sets) . ' WHERE id = ?')->execute($vals);

$stmt = $pdo->prepare('SELECT id, name, username, email, phone_number, is_admin, updated_at FROM users WHERE id = ?');
$stmt->execute([$id]);
$user = $stmt->fetch();
$user['id'] = (int) $user['id'];
$user['is_admin'] = (int) $user['is_admin'];
respond(['user' => $user]);
