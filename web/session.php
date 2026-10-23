<?php
/**
 * Dove Session Engine
 * Lightweight, procedural session coordinator and lifecycle manager.
 */

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');

$sessionsDir = __DIR__ . '/sessions';
if (!is_dir($sessionsDir)) {
    @mkdir($sessionsDir, 0755, true);
}
if (!is_dir($sessionsDir) || !is_writable($sessionsDir)) {
    $sessionsDir = sys_get_temp_dir() . '/dove-sessions';
    if (!is_dir($sessionsDir)) {
        @mkdir($sessionsDir, 0755, true);
    }
}

$action = $_GET['action'] ?? $_POST['action'] ?? 'status';
$sessionId = preg_replace('/[^0-9]/', '', (string)($_GET['session'] ?? $_POST['session'] ?? ''));

function sendJson(array $data, int $statusCode = 200): void {
    http_response_code($statusCode);
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit;
}

if (empty($sessionId)) {
    sendJson(['error' => 'Missing or invalid session ID'], 400);
}

$sessionFile = $sessionsDir . '/' . $sessionId . '.json';
$receivedFile = $sessionsDir . '/' . $sessionId . '_received.bin';
$metaFile = $sessionsDir . '/' . $sessionId . '_meta.json';

switch ($action) {
    case 'info':
        if (!file_exists($sessionFile)) {
            sendJson(['error' => 'Session not found or expired', 'expired' => true], 404);
        }

        $content = file_get_contents($sessionFile);
        $data = json_decode($content, true);
        if (!$data) {
            sendJson(['error' => 'Corrupt session state'], 500);
        }

        $now = time();
        if (!empty($data['expires_at']) && $data['expires_at'] < $now) {
            // Session expired: self-destruct
            @unlink($sessionFile);
            sendJson(['error' => 'Session has expired and was destroyed', 'expired' => true], 410);
        }

        $remainingSeconds = !empty($data['expires_at']) ? max(0, $data['expires_at'] - $now) : null;

        sendJson([
            'session_id' => $data['session_id'],
            'file_name' => $data['file_name'],
            'file_size' => $data['file_size'],
            'total_chunks' => $data['total_chunks'],
            'chunk_size' => $data['chunk_size'],
            'grid' => $data['grid'] ?? '3x3',
            'fps' => $data['fps'] ?? 26,
            'expires_at' => $data['expires_at'],
            'remaining_seconds' => $remainingSeconds,
            'total_frames' => count($data['frames'] ?? []),
            'has_pin' => !empty($data['has_pin']),
            'expired' => false,
        ]);
        break;

    case 'frames':
        if (!file_exists($sessionFile)) {
            sendJson(['error' => 'Session not found or expired', 'expired' => true], 404);
        }

        $content = file_get_contents($sessionFile);
        $data = json_decode($content, true);
        if (!$data || empty($data['frames'])) {
            sendJson(['error' => 'No frames available'], 404);
        }

        sendJson([
            'session_id' => $data['session_id'],
            'frames' => $data['frames'],
            'count' => count($data['frames']),
        ]);
        break;

    case 'import':
        if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
            sendJson(['error' => 'Method not allowed'], 405);
        }

        $rawInput = file_get_contents('php://input');
        if (empty($rawInput)) {
            sendJson(['error' => 'Empty payload received'], 400);
        }

        $postData = json_decode($rawInput, true);
        $binaryData = null;
        $fileName = 'received_' . time() . '.bin';

        if (isset($postData['payload_base64'])) {
            $binaryData = base64_decode($postData['payload_base64']);
            if (isset($postData['file_name']) && !empty($postData['file_name'])) {
                $fileName = basename($postData['file_name']);
            }
        } else {
            $binaryData = $rawInput;
        }

        if (empty($binaryData)) {
            sendJson(['error' => 'Invalid or unparseable binary payload'], 400);
        }

        file_put_contents($receivedFile, $binaryData);
        file_put_contents($metaFile, json_encode([
            'session_id' => (int)$sessionId,
            'file_name' => $fileName,
            'file_size' => strlen($binaryData),
            'timestamp' => time(),
        ]));

        sendJson([
            'status' => 'success',
            'session_id' => (int)$sessionId,
            'bytes_written' => strlen($binaryData),
            'file_name' => $fileName,
        ]);
        break;

    case 'destroy':
        @unlink($sessionFile);
        @unlink($receivedFile);
        @unlink($metaFile);
        sendJson(['status' => 'destroyed', 'session_id' => (int)$sessionId]);
        break;

    default:
        sendJson(['error' => 'Unknown action: ' . htmlspecialchars($action)], 400);
        break;
}
