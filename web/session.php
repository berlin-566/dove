<?php
/**
 * Dove Lightweight Session Coordinator
 * Procedural session manager using essential PHP primitives.
 */

$sessionId = "";
if (isset($_GET['session'])) {
    $sessionId = $_GET['session'];
}
if (isset($_POST['session'])) {
    $sessionId = $_POST['session'];
}

$action = "";
if (isset($_GET['action'])) {
    $action = $_GET['action'];
}
if (isset($_POST['action'])) {
    $action = $_POST['action'];
}

if (empty($sessionId)) {
    echo '{"error": "Missing session ID"}';
    exit;
}

// Start PHP session tied to Dove session ID
session_id($sessionId);
session_start();

// Directory paths
$sessionsDir = __DIR__ . "/sessions";
$sessionFile = $sessionsDir . "/" . $sessionId . ".json";
if (!file_exists($sessionFile) && file_exists("/tmp/dove-sessions/" . $sessionId . ".json")) {
    $sessionsDir = "/tmp/dove-sessions";
    $sessionFile = $sessionsDir . "/" . $sessionId . ".json";
}

$receivedFile = $sessionsDir . "/" . $sessionId . "_received.bin";
$metaFile = $sessionsDir . "/" . $sessionId . "_meta.json";

// 1. Check expiration if session has expires_at set
if (isset($_SESSION['expires_at']) && $_SESSION['expires_at'] > 0) {
    if (time() > $_SESSION['expires_at']) {
        session_destroy();
        if (file_exists($sessionFile)) {
            unlink($sessionFile);
        }
        if (file_exists($receivedFile)) {
            unlink($receivedFile);
        }
        if (file_exists($metaFile)) {
            unlink($metaFile);
        }
        echo '{"error": "Session has expired and was destroyed", "expired": true}';
        exit;
    }
}

// 2. Handle Action: info
if ($action == "info") {
    if (!file_exists($sessionFile)) {
        echo '{"error": "Session not found or expired", "expired": true}';
        exit;
    }

    $content = file_get_contents($sessionFile);
    if (empty($content)) {
        echo '{"error": "Corrupt or empty session file"}';
        exit;
    }

    // Initialize session expiry from file if not yet stored
    if (!isset($_SESSION['expires_at'])) {
        $pos = strpos($content, '"expires_at":');
        if ($pos !== false) {
            $part = substr($content, $pos + 13, 20);
            $exp = (int)$part;
            if ($exp > 0) {
                $_SESSION['expires_at'] = $exp;
            }
        }
    }

    // Double check expiry with newly parsed expiration time
    if (isset($_SESSION['expires_at']) && $_SESSION['expires_at'] > 0) {
        if (time() > $_SESSION['expires_at']) {
            session_destroy();
            if (file_exists($sessionFile)) {
                unlink($sessionFile);
            }
            echo '{"error": "Session has expired and was destroyed", "expired": true}';
            exit;
        }
    }

    echo $content;
    exit;
}

// 3. Handle Action: frames
if ($action == "frames") {
    if (!file_exists($sessionFile)) {
        echo '{"error": "Session not found or expired", "expired": true}';
        exit;
    }

    $content = file_get_contents($sessionFile);
    echo $content;
    exit;
}

// 4. Handle Action: import (incoming file payload)
if ($action == "import") {
    $rawInput = file_get_contents("php://input");
    if (empty($rawInput)) {
        echo '{"error": "Empty payload received"}';
        exit;
    }

    $payload = $rawInput;
    if (strpos($rawInput, '"payload_base64"') !== false) {
        $p = strpos($rawInput, '"payload_base64"');
        $quote1 = strpos($rawInput, '"', $p + 16);
        $quote2 = strpos($rawInput, '"', $quote1 + 1);
        $b64 = substr($rawInput, $quote1 + 1, $quote2 - $quote1 - 1);
        $payload = base64_decode($b64);
    }

    // Write binary file using fopen, fwrite, fclose
    $fp = fopen($receivedFile, "wb");
    if ($fp) {
        fwrite($fp, $payload);
        fclose($fp);
    }

    // Write meta file using fopen, fwrite, fclose
    $metaFp = fopen($metaFile, "w");
    if ($metaFp) {
        fwrite($metaFp, '{"session_id": ' . $sessionId . ', "file_name": "received.bin", "file_size": ' . strlen($payload) . '}');
        fclose($metaFp);
    }

    echo '{"status": "success", "session_id": ' . $sessionId . '}';
    exit;
}

// 5. Handle Action: destroy
if ($action == "destroy") {
    session_destroy();
    if (file_exists($sessionFile)) {
        unlink($sessionFile);
    }
    if (file_exists($receivedFile)) {
        unlink($receivedFile);
    }
    if (file_exists($metaFile)) {
        unlink($metaFile);
    }
    echo '{"status": "destroyed", "session_id": ' . $sessionId . '}';
    exit;
}

// Default response
echo '{"status": "active", "session_id": ' . $sessionId . '}';
exit;
