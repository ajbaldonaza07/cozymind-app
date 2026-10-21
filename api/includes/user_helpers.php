<?php
/** Validation + insert shared by authentication/register.php and users/create.php. */

declare(strict_types=1);

/** Validates a new-user request body and returns clean values (or stops with 422). */
function validate_new_user(array $body): array
{
    $name     = field($body, 'name', 100);
    $username = field($body, 'username', 50);
    $email    = strtolower(field($body, 'email', 190));
    $phone    = field($body, 'phone_number', 30);
    $password = isset($body['password']) && is_string($body['password']) ? $body['password'] : '';

    if ($name === '') fail('Enter your name.', 422);
    if (!preg_match('/^[A-Za-z0-9_.]{3,50}$/', $username))
        fail('Username must be 3–50 letters, numbers, dots or underscores.', 422);
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Enter a valid email address.', 422);
    if (strlen(preg_replace('/\D/', '', $phone)) < 7) fail('Enter a valid phone number.', 422);
    if (strlen($password) < 6)  fail('Use at least 6 characters.', 422);
    if (strlen($password) > 72) fail('Password is too long.', 422);

    return compact('name', 'username', 'email', 'phone', 'password');
}

/** Inserts the user (password hashed) and returns the new id. Stops with 409 on duplicates. */
function insert_user(array $u, int $isAdmin = 0): int
{
    $pdo = db();
    $check = $pdo->prepare('SELECT username, email FROM users WHERE username = ? OR email = ? LIMIT 1');
    $check->execute([$u['username'], $u['email']]);
    if ($existing = $check->fetch()) {
        fail(strcasecmp($existing['email'], $u['email']) === 0
            ? 'That email already has an account.'
            : 'That username is taken.', 409);
    }

    $now = now_utc();
    $insert = $pdo->prepare(
        'INSERT INTO users (name, username, password, email, phone_number, is_admin, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    );
    try {
        $insert->execute([$u['name'], $u['username'], password_hash($u['password'], PASSWORD_DEFAULT),
                          $u['email'], $u['phone'], $isAdmin, $now, $now]);
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') fail('That username or email is already registered.', 409);
        throw $e;
    }
    return (int) $pdo->lastInsertId();
}
