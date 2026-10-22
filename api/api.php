<?php
/**
 * Cozymind API — single-file JSON backend for the Cozymind Android app.
 *
 * Upload to: htdocs/cozymind-api/api.php  (next to config.php)
 * App URL:   https://baldonaza.byethost31.com/cozymind-api/api.php
 *
 * Actions (?action=...):
 *   health    GET   → {"ok":true}
 *   register  POST  {name, username, email, phone_number, password} → {user}
 *   login     POST  {identity, password}  (identity = email OR username) → {user}
 *   logout    POST
 *   moods     POST  {severity 1-5}  |  GET → {moods, statistics_6h}
 *   journals  POST  {title, body}   |  GET → {journals}
 *
 * Signed-in state uses the standard PHP session cookie (PHPSESSID), which the app stores.
 * All times are stored and returned in UTC as "YYYY-MM-DD HH:MM:SS".
 */

declare(strict_types=1);

// Never print PHP warnings into the response — they break the app's JSON parsing.
ini_set('display_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

// Turn any uncaught error into a clean JSON 500 instead of an HTML error page.
set_exception_handler(function (Throwable $e): void {
    error_log('Cozymind API error: ' . $e->getMessage());
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
    }
    echo json_encode(['error' => 'Server error. Please try again later.']);
});

// ---------- helpers ----------

function respond(array $data, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function fail(string $message, int $status): void
{
    respond(['error' => $message], $status);
}

function read_json_body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') return [];
    $data = json_decode($raw, true);
    if (!is_array($data)) fail('Request body must be valid JSON.', 400);
    return $data;
}

function field(array $body, string $key, int $maxLength): string
{
    $value = isset($body[$key]) && is_scalar($body[$key]) ? trim((string) $body[$key]) : '';
    if ((function_exists('mb_strlen') ? mb_strlen($value) : strlen($value)) > $maxLength) fail("The $key is too long.", 422);
    return $value;
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;

    $config = require __DIR__ . '/config.php';
    $dsn = "mysql:host={$config['db_host']};dbname={$config['db_name']};charset=utf8mb4";
    try {
        $pdo = new PDO($dsn, $config['db_user'], $config['db_pass'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    } catch (PDOException $e) {
        error_log('Cozymind DB connection failed: ' . $e->getMessage());
        fail('The database is not reachable right now.', 503);
    }
    $pdo->exec("SET time_zone = '+00:00'");
    return $pdo;
}

function now_utc(): string
{
    return gmdate('Y-m-d H:i:s');
}

function public_user(array $user): array
{
    return [
        'id'       => (int) $user['id'],
        'name'     => $user['name'],
        'username' => $user['username'],
        'email'    => $user['email'],
    ];
}

function require_user_id(): int
{
    if (empty($_SESSION['user_id'])) fail('Please sign in again.', 401);
    return (int) $_SESSION['user_id'];
}

// ---------- session ----------

session_set_cookie_params([
    'lifetime' => 60 * 60 * 24 * 30,   // stay signed in for 30 days
    'path'     => '/',
    // Secure cookies only over HTTPS (ByetHost); plain HTTP is used on Laragon.
    'secure'   => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    'httponly' => true,
    'samesite' => 'Lax',
]);
ini_set('session.gc_maxlifetime', (string) (60 * 60 * 24 * 30));
session_start();

// ---------- routing ----------

$action = $_GET['action'] ?? '';
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

switch ($action) {

    case 'health':
        respond(['ok' => true, 'time' => now_utc()]);

    case 'register':
        if ($method !== 'POST') fail('Use POST.', 405);
        $body     = read_json_body();
        $name     = field($body, 'name', 100);
        $username = field($body, 'username', 50);
        $email    = strtolower(field($body, 'email', 190));
        $phone    = field($body, 'phone_number', 30);
        $password = isset($body['password']) && is_string($body['password']) ? $body['password'] : '';

        if ($name === '')                                   fail('Enter your name.', 422);
        if (!preg_match('/^[A-Za-z0-9_.]{3,50}$/', $username))
            fail('Username must be 3–50 letters, numbers, dots or underscores.', 422);
        if (!filter_var($email, FILTER_VALIDATE_EMAIL))      fail('Enter a valid email address.', 422);
        if (strlen(preg_replace('/\D/', '', $phone)) < 7)    fail('Enter a valid phone number.', 422);
        if (strlen($password) < 6)                          fail('Use at least 6 characters.', 422);
        if (strlen($password) > 72)                         fail('Password is too long.', 422);

        $pdo = db();
        $check = $pdo->prepare('SELECT username, email FROM users WHERE username = ? OR email = ? LIMIT 1');
        $check->execute([$username, $email]);
        if ($existing = $check->fetch()) {
            fail(strcasecmp($existing['email'], $email) === 0
                ? 'That email already has an account.'
                : 'That username is taken.', 409);
        }

        $now = now_utc();
        $insert = $pdo->prepare(
            'INSERT INTO users (name, username, password, email, phone_number, is_admin, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, 0, ?, ?)'
        );
        try {
            $insert->execute([$name, $username, password_hash($password, PASSWORD_DEFAULT),
                              $email, $phone, $now, $now]);
        } catch (PDOException $e) {
            if ($e->getCode() === '23000') fail('That username or email is already registered.', 409);
            throw $e;
        }

        $user = ['id' => (int) $pdo->lastInsertId(), 'name' => $name, 'username' => $username, 'email' => $email];
        session_regenerate_id(true);
        $_SESSION['user_id'] = $user['id'];
        respond(['user' => public_user($user)], 201);

    case 'login':
        if ($method !== 'POST') fail('Use POST.', 405);
        $body     = read_json_body();
        $identity = field($body, 'identity', 190);
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

    case 'logout':
        $_SESSION = [];
        session_destroy();
        respond(['ok' => true]);

    case 'moods':
        $userId = require_user_id();
        $pdo = db();

        if ($method === 'POST') {
            $body = read_json_body();
            $severity = isset($body['severity']) ? (int) $body['severity'] : 0;
            if ($severity < 1 || $severity > 5) fail('Severity must be between 1 and 5.', 422);
            $now = now_utc();
            $pdo->prepare('INSERT INTO mood_trackers (user_id, datetime_mood, severity, created_at) VALUES (?, ?, ?, ?)')
                ->execute([$userId, $now, $severity, $now]);
            respond(['ok' => true, 'mood' => ['severity' => $severity, 'datetime_mood' => $now]], 201);
        }

        if ($method === 'GET') {
            $moods = $pdo->prepare(
                'SELECT severity, datetime_mood FROM mood_trackers
                 WHERE user_id = ? ORDER BY datetime_mood DESC LIMIT 30'
            );
            $moods->execute([$userId]);

            // Average per 6-hour window, only for windows with at least 3 check-ins (newest first).
            $stats = $pdo->prepare(
                'SELECT FLOOR(UNIX_TIMESTAMP(datetime_mood) / 21600) * 21600 AS period_start,
                        AVG(severity) AS average_severity,
                        COUNT(*)      AS entry_count
                 FROM mood_trackers
                 WHERE user_id = ?
                 GROUP BY period_start
                 HAVING COUNT(*) >= 3
                 ORDER BY period_start DESC
                 LIMIT 20'
            );
            $stats->execute([$userId]);

            respond([
                'moods' => array_map(fn($m) => [
                    'severity'      => (int) $m['severity'],
                    'datetime_mood' => $m['datetime_mood'],
                ], $moods->fetchAll()),
                'statistics_6h' => array_map(fn($s) => [
                    'period_start'     => gmdate('Y-m-d H:i:s', (int) $s['period_start']),
                    'average_severity' => round((float) $s['average_severity'], 2),
                    'entry_count'      => (int) $s['entry_count'],
                ], $stats->fetchAll()),
            ]);
        }
        fail('Use GET or POST.', 405);

    case 'journals':
        $userId = require_user_id();
        $pdo = db();

        if ($method === 'POST') {
            $body  = read_json_body();
            $title = field($body, 'title', 150);
            $text  = field($body, 'body', 20000);
            if ($text === '') fail('Write a few words before saving.', 422);
            if ($title === '') $title = 'A quiet moment';
            $now = now_utc();
            $pdo->prepare('INSERT INTO journals (user_id, title, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?)')
                ->execute([$userId, $title, $text, $now, $now]);
            respond(['ok' => true, 'journal' => ['title' => $title, 'body' => $text, 'created_at' => $now]], 201);
        }

        if ($method === 'GET') {
            $stmt = $pdo->prepare(
                'SELECT title, body, created_at FROM journals
                 WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 50'
            );
            $stmt->execute([$userId]);
            respond(['journals' => $stmt->fetchAll()]);
        }
        fail('Use GET or POST.', 405);

    default:
        fail('Unknown action.', 404);
}
