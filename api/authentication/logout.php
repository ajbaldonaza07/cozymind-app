<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('POST');

$_SESSION = [];
session_destroy();
respond(['ok' => true]);
