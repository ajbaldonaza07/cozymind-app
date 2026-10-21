<?php
/**
 * Shared setup for every endpoint file: JSON headers, error handling, DB, session, helpers.
 * Each endpoint does:  require_once __DIR__ . '/../includes/bootstrap.php';
 */

declare(strict_types=1);

// Never print PHP warnings into the response — they break JSON parsing.
ini_set('display_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

set_exception_handler(function (Throwable $e): void {
    error_log('Cozymind API error: ' . $e->getMessage());
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
    }
    echo json_encode(['error' => 'Server error. Please try again later.']);
});

// ---------- response helpers ----------

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

/** Stop with 405 unless the request method is one of the allowed ones. */
function require_method(string ...$allowed): void
{
    $method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
    if (!in_array($method, $allowed, true)) {
        header('Allow: ' . implode(', ', $allowed));
        fail('Use ' . implode(' or ', $allowed) . '.', 405);
    }
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
    if ((function_exists('mb_strlen') ? mb_strlen($value) : strlen($value)) > $maxLength) {
        fail("The $key is too long.", 422);
    }
    return $value;
}

/** Record id from ?id=... or from the JSON body {"id": ...}. Stops with 422 if missing/invalid. */
function get_id(array $body = []): int
{
    $id = $_GET['id'] ?? ($body['id'] ?? null);
    if (!is_scalar($id) || !preg_match('/^\d+$/', (string) $id) || (int) $id < 1) {
        fail('A valid id is required.', 422);
    }
    return (int) $id;
}

/** Severity 1-5 from the body; rejects non-numeric values instead of silently casting them. */
function get_severity(array $body): int
{
    $v = $body['severity'] ?? null;
    if (!is_scalar($v) || !preg_match('/^\d+$/', (string) $v) || (int) $v < 1 || (int) $v > 5) {
        fail('Severity must be a whole number between 1 and 5.', 422);
    }
    return (int) $v;
}

// ---------- database ----------

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;

    $config = require __DIR__ . '/../config.php';
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

// ---------- session / auth ----------

session_set_cookie_params([
    'lifetime' => 60 * 60 * 24 * 30,   // stay signed in for 30 days
    'path'     => '/',
    'secure'   => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    'httponly' => true,
    'samesite' => 'Lax',
]);
ini_set('session.gc_maxlifetime', (string) (60 * 60 * 24 * 30));
session_start();

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

function is_admin(int $userId): bool
{
    $stmt = db()->prepare('SELECT is_admin FROM users WHERE id = ?');
    $stmt->execute([$userId]);
    $row = $stmt->fetch();
    return $row && (int) $row['is_admin'] === 1;
}

/** Signed-in AND is_admin = 1, otherwise 403. Returns the admin's user id. */
function require_admin(): int
{
    $userId = require_user_id();
    if (!is_admin($userId)) fail('Admins only.', 403);
    return $userId;
}


// ---------- validators (goals, sleep logs) ----------

const GOAL_FREQUENCIES = ['daily', 'weekly'];
const GOAL_STATUSES    = ['active', 'done', 'dropped'];

/** "YYYY-MM-DD" (a real calendar date) or stops with 422. */
function valid_date($value, string $key): string
{
    $value = is_string($value) ? trim($value) : '';
    $d = DateTime::createFromFormat('!Y-m-d', $value);
    if (!$d || $d->format('Y-m-d') !== $value) {
        fail("The $key must be a date like 2026-10-22.", 422);
    }
    return $value;
}

/** "HH:MM" or "HH:MM:SS" -> "HH:MM:SS", or stops with 422. */
function valid_time($value, string $key): string
{
    if (!is_string($value) || !preg_match('/^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/', trim($value), $m)) {
        fail("The $key must be a time like 22:30 or 22:30:00.", 422);
    }
    return sprintf('%02d:%02d:%02d', (int) $m[1], (int) $m[2], (int) ($m[3] ?? 0));
}

/** One of $allowed (case-insensitive) or stops with 422. */
function enum_field($value, string $key, array $allowed): string
{
    $value = is_string($value) ? strtolower(trim($value)) : '';
    if (!in_array($value, $allowed, true)) {
        fail("The $key must be one of: " . implode(', ', $allowed) . '.', 422);
    }
    return $value;
}

/** Whole number between $min and $max or stops with 422. */
function int_in_range($value, string $key, int $min, int $max): int
{
    if (!is_scalar($value) || !preg_match('/^\d+$/', (string) $value) || (int) $value < $min || (int) $value > $max) {
        fail("The $key must be a whole number between $min and $max.", 422);
    }
    return (int) $value;
}
