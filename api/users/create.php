<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_once __DIR__ . '/../includes/user_helpers.php';
require_method('POST');
require_admin();

$body    = read_json_body();
$u       = validate_new_user($body);
$isAdmin = !empty($body['is_admin']) ? 1 : 0;
$id      = insert_user($u, $isAdmin);

respond(['user' => [
    'id' => $id, 'name' => $u['name'], 'username' => $u['username'],
    'email' => $u['email'], 'phone_number' => $u['phone'], 'is_admin' => $isAdmin,
]], 201);
