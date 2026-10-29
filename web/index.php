<?php
// Dove Simple Web Dispatcher

$session = "";
if (isset($_GET['session'])) {
    $session = $_GET['session'];
}

$action = "";
if (isset($_GET['action'])) {
    $action = $_GET['action'];
}

if (!empty($session)) {
    if ($action == 'scan') {
        include 'scanner.html';
        exit;
    }
    include 'player.html';
    exit;
}

echo "Dove Optical File Sharing Engine\n";
echo "Status: Running on local port\n";
echo "Documentation: https://github.com/berlin-566/dove\n";
exit;
