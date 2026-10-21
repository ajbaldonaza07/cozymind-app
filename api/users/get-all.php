<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
require_admin();

$rows = db()->query(
    'SELECT id, name, username, email, phone_number, is_admin, created_at, updated_at
     FROM users ORDER BY id ASC LIMIT 200'
)->fetchAll();

respond(['users' => array_map(function ($r) {
    $r['id'] = (int) $r['id'];
    $r['is_admin'] = (int) $r['is_admin'];
    return $r;
}, $rows)]);
