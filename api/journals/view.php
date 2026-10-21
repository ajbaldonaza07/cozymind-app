<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();
$id = get_id();   // ?id=1

$stmt = db()->prepare('SELECT id, title, body, created_at, updated_at FROM journals WHERE id = ? AND user_id = ?');
$stmt->execute([$id, $userId]);
$journal = $stmt->fetch();
if (!$journal) fail('Journal not found.', 404);

$journal['id'] = (int) $journal['id'];
respond(['journal' => $journal]);
