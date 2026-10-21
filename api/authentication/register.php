<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_once __DIR__ . '/../includes/user_helpers.php';
require_method('POST');

$u  = validate_new_user(read_json_body());
$id = insert_user($u, 0);   // self-registered accounts are never admins

session_regenerate_id(true);
$_SESSION['user_id'] = $id;
respond(['user' => public_user([
    'id' => $id, 'name' => $u['name'], 'username' => $u['username'], 'email' => $u['email'],
])], 201);
