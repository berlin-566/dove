<?php
/**
 * Dove Web Dispatcher
 */

$sessionId = $_GET['session'] ?? '';
$action = $_GET['action'] ?? '';

if (!empty($sessionId)) {
    if ($action === 'scan') {
        header('Location: scanner.html?session=' . urlencode($sessionId));
        exit;
    }
    header('Location: player.html?session=' . urlencode($sessionId));
    exit;
}

header('Content-Type: text/plain; charset=utf-8');
echo "Dove Optical File Sharing Engine\n";
echo "Status: Running on local port\n";
echo "Documentation: https://github.com/berlin-566/dove\n";
