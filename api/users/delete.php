<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST', 'DELETE');
$me = require_user_id();

// Grab ID from query string or JSON body, and cast it to an integer
$id = isset($_GET['id']) ? (int)$_GET['id'] : get_id(read_json_body());

// Now both $id and $me are integers, so comparison works correctly
if ($id !== $me && !is_admin($me)) {
    fail('You can only delete your own account.', 403);
}

$stmt = db()->prepare('DELETE FROM users WHERE id = ?'); // journals + moods go too (ON DELETE CASCADE)
$stmt->execute([$id]);

if ($stmt->rowCount() === 0) {
    fail('User not found.', 404);
}

if ($id === $me) { // deleted own account → sign out
    $_SESSION = [];
    session_destroy();
}

respond(['ok' => true, 'deleted_id' => $id]);