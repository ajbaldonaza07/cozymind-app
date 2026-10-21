<?php
require_once __DIR__ . '/../includes/bootstrap.php';
require_method('GET');
$userId = require_user_id();
$pdo = db();

$moods = $pdo->prepare(
    'SELECT id, severity, datetime_mood FROM mood_trackers
     WHERE user_id = ? ORDER BY datetime_mood DESC, id DESC LIMIT 100'
);
$moods->execute([$userId]);

// Average per 6-hour window, only for windows with at least 3 check-ins (newest first).
$stats = $pdo->prepare(
    'SELECT FLOOR(UNIX_TIMESTAMP(datetime_mood) / 21600) * 21600 AS period_start,
            AVG(severity) AS average_severity, COUNT(*) AS entry_count
     FROM mood_trackers WHERE user_id = ?
     GROUP BY period_start HAVING COUNT(*) >= 3
     ORDER BY period_start DESC LIMIT 20'
);
$stats->execute([$userId]);

respond([
    'moods' => array_map(fn($m) => [
        'id'            => (int) $m['id'],
        'severity'      => (int) $m['severity'],
        'datetime_mood' => $m['datetime_mood'],
    ], $moods->fetchAll()),
    'statistics_6h' => array_map(fn($s) => [
        'period_start'     => gmdate('Y-m-d H:i:s', (int) $s['period_start']),
        'average_severity' => round((float) $s['average_severity'], 2),
        'entry_count'      => (int) $s['entry_count'],
    ], $stats->fetchAll()),
]);
