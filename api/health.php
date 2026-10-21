<?php
require_once __DIR__ . '/includes/bootstrap.php';
require_method('GET');
respond(['ok' => true, 'time' => now_utc()]);
