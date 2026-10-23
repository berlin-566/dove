package cmd

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/berlin-566/dove/pkg/crypto"
	"github.com/berlin-566/dove/pkg/fountain"
	"github.com/berlin-566/dove/pkg/qr"
	"github.com/berlin-566/dove/pkg/server"
	qrcode "github.com/skip2/go-qrcode"
)

type SessionPayload struct {
	SessionID   uint64   `json:"session_id"`
	FileName    string   `json:"file_name"`
	FileSize    int64    `json:"file_size"`
	TotalChunks int      `json:"total_chunks"`
	ChunkSize   int      `json:"chunk_size"`
	Grid        string   `json:"grid"` // "1x1", "2x2", "3x3", "4x3"
	Fps         int      `json:"fps"`
	ExpiresAt   int64    `json:"expires_at"` // 0 if unlimited
	Frames      []string `json:"frames"`     // Pre-generated fountain frames in base64
	HasPin      bool     `json:"has_pin"`
}

// RunSend executes the 'dove send' command logic.
func RunSend(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("missing target file path. Usage: dove send <filename> [--<N>m] [--terminal]")
	}

	var filePath string
	var duration time.Duration
	var pin string
	var useTerminal bool
	grid := "3x3"
	fps := 26
	fpsExplicitlySet := false
	chunkSizeOverride := 0

	for i := 0; i < len(args); i++ {
		arg := args[i]
		if (strings.HasPrefix(arg, "--") || strings.HasPrefix(arg, "-")) && strings.HasSuffix(arg, "m") && len(arg) >= 3 && arg != "-1" {
			// Minutes format: e.g. --100m, --1m, -1m, -10m
			trim := strings.TrimLeft(arg, "-")
			minStr := trim[:len(trim)-1]
			mins, err := strconv.Atoi(minStr)
			if err != nil {
				return fmt.Errorf("invalid duration format '%s': expected --<N>m or -<N>m", arg)
			}
			duration = time.Duration(mins) * time.Minute
		} else if arg == "-1" || arg == "--1" || arg == "--single" {
			grid = "1x1"
		} else if arg == "-4" || arg == "--4" {
			grid = "2x2"
		} else if arg == "-9" || arg == "--9" {
			grid = "3x3"
		} else if arg == "-12" || arg == "--12" {
			grid = "4x3"
		} else if arg == "--terminal" || arg == "-t" {
			useTerminal = true
		} else if (arg == "--pin" || arg == "-p") && i+1 < len(args) {
			pin = args[i+1]
			i++
		} else if arg == "--fps" && i+1 < len(args) {
			f, err := strconv.Atoi(args[i+1])
			if err == nil && f >= 5 && f <= 30 {
				fps = f
				fpsExplicitlySet = true
			}
			i++
		} else if (arg == "--grid" || arg == "-g") && i+1 < len(args) {
			g := strings.ToLower(args[i+1])
			if g == "1x1" || g == "2x2" || g == "3x3" || g == "4x3" {
				grid = g
			}
			i++
		} else if arg == "--chunk-size" && i+1 < len(args) {
			cs, err := strconv.Atoi(args[i+1])
			if err == nil && cs >= 64 && cs <= 800 {
				chunkSizeOverride = cs
			}
			i++
		} else if !strings.HasPrefix(arg, "-") && filePath == "" {
			filePath = arg
		}
	}

	if !fpsExplicitlySet {
		if grid == "1x1" {
			fps = 16
		} else {
			fps = 26
		}
	}

	if filePath == "" {
		return fmt.Errorf("missing target file path")
	}

	fileInfo, err := os.Stat(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file '%s': %w", filePath, err)
	}
	if fileInfo.IsDir() {
		return fmt.Errorf("path '%s' is a directory; please provide a file", filePath)
	}

	rawBytes, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("failed to read file '%s': %w", filePath, err)
	}

	var sessionRand [8]byte
	_, _ = rand.Read(sessionRand[:])
	sessionID := binary.BigEndian.Uint64(sessionRand[:]) & 0x7FFFFFFFFFFFFFFF

	var key []byte
	if pin != "" {
		salt := []byte(fmt.Sprintf("dove:%d", sessionID))
		key = crypto.DeriveKeyFromPin(pin, salt)
	} else {
		key, err = crypto.GenerateKey()
		if err != nil {
			return fmt.Errorf("failed to generate encryption key: %w", err)
		}
	}

	// Pack metadata header: [2 bytes fileNameLen][fileName][rawBytes]
	fileName := filepath.Base(filePath)
	fileNameBytes := []byte(fileName)
	packedData := make([]byte, 2+len(fileNameBytes)+len(rawBytes))
	binary.BigEndian.PutUint16(packedData[0:2], uint16(len(fileNameBytes)))
	copy(packedData[2:2+len(fileNameBytes)], fileNameBytes)
	copy(packedData[2+len(fileNameBytes):], rawBytes)

	// Encrypt packed payload
	encryptedData, err := crypto.Encrypt(packedData, key)
	if err != nil {
		return fmt.Errorf("encryption failed: %w", err)
	}

	// Prepend key if no pin is specified (seamless optical decryption)
	var innerPayload []byte
	if pin == "" {
		innerPayload = make([]byte, len(key)+len(encryptedData))
		copy(innerPayload[:len(key)], key)
		copy(innerPayload[len(key):], encryptedData)
	} else {
		innerPayload = encryptedData
	}

	// Final payload with exact length header: [4 bytes innerPayloadLen][innerPayload]
	finalPayload := make([]byte, 4+len(innerPayload))
	binary.BigEndian.PutUint32(finalPayload[0:4], uint32(len(innerPayload)))
	copy(finalPayload[4:], innerPayload)

	payloadLen := len(finalPayload)
	chunkSize := fountain.CalculateOptimalChunkSize(payloadLen)
	if chunkSizeOverride > 0 {
		chunkSize = chunkSizeOverride
	}

	encoder := fountain.NewEncoder(sessionID, finalPayload, chunkSize)

	// Pre-generate enough fountain frames to guarantee complete reconstruction
	// Sized proportionally to total chunks, scaling dynamically up to 8,000 for 1MB+
	frameCount := int(encoder.TotalChunks)*2 + 100
	if frameCount < 300 {
		frameCount = 300
	}
	if frameCount > 8000 {
		frameCount = 8000
	}

	frames := make([]string, frameCount)
	for i := 0; i < frameCount; i++ {
		sym := encoder.NextSymbol()
		frames[i] = base64.StdEncoding.EncodeToString(sym.Serialize())
	}

	var expiresAt int64
	if duration > 0 {
		expiresAt = time.Now().Add(duration).Unix()
	}

	// Trap termination signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	if useTerminal {
		return runTerminalStream(encoder, frames, filePath, len(rawBytes), duration, fps, sigChan)
	}

	// Web browser mode
	webDir, err := server.LocateWebDir()
	if err != nil {
		return fmt.Errorf("web directory not found: %w\nRun with --terminal to stream in terminal instead", err)
	}

	port, err := server.FindAvailablePort()
	if err != nil {
		return fmt.Errorf("failed to allocate port: %w", err)
	}

	// Write session file into webDir/sessions/ with fallback
	sessionsDir := filepath.Join(webDir, "sessions")
	if err := os.MkdirAll(sessionsDir, 0755); err != nil {
		sessionsDir = filepath.Join(os.TempDir(), "dove-sessions")
		_ = os.MkdirAll(sessionsDir, 0755)
	}

	sessionFile := filepath.Join(sessionsDir, fmt.Sprintf("%d.json", sessionID))
	payload := SessionPayload{
		SessionID:   sessionID,
		FileName:    filepath.Base(filePath),
		FileSize:    fileInfo.Size(),
		TotalChunks: int(encoder.TotalChunks),
		ChunkSize:   chunkSize,
		Grid:        grid,
		Fps:         fps,
		ExpiresAt:   expiresAt,
		Frames:      frames,
		HasPin:      pin != "",
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("failed to serialize session metadata: %w", err)
	}
	if err := os.WriteFile(sessionFile, data, 0644); err != nil {
		sessionsDir = filepath.Join(os.TempDir(), "dove-sessions")
		_ = os.MkdirAll(sessionsDir, 0755)
		sessionFile = filepath.Join(sessionsDir, fmt.Sprintf("%d.json", sessionID))
		if err2 := os.WriteFile(sessionFile, data, 0644); err2 != nil {
			return fmt.Errorf("failed to save session state: %w (fallback error: %v)", err, err2)
		}
	}
	defer os.Remove(sessionFile)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	srv, err := server.Start(ctx, port, webDir)
	if err != nil {
		return fmt.Errorf("failed to start PHP server: %w", err)
	}
	defer srv.Stop()

	targetURL := fmt.Sprintf("http://127.0.0.1:%d/player.html?session=%d", port, sessionID)
	_ = server.OpenBrowser(targetURL)

	fmt.Println("Dove: Optical Stream Active")
	fmt.Printf("File: %s (%d bytes)\n", filepath.Base(filePath), fileInfo.Size())
	fmt.Printf("Session ID: %d\n", sessionID)
	fmt.Printf("Grid Layout: %s (%d FPS)\n", grid, fps)
	fmt.Printf("Browser URL: %s\n", targetURL)
	if duration > 0 {
		fmt.Printf("Duration: %s (auto-destructs at %s)\n", duration, time.Unix(expiresAt, 0).Format("15:04:05"))
	} else {
		fmt.Println("Duration: unlimited (press Ctrl+C to terminate)")
	}
	fmt.Println("Status: Broadcasting rateless optical fountain. Point Android camera at screen.")

	var timer <-chan time.Time
	if duration > 0 {
		timer = time.After(duration)
	}

	select {
	case <-timer:
		fmt.Println("\nSession time expired. Destroying session and terminating server.")
	case <-sigChan:
		fmt.Println("\nTermination signal received. Cleaning up session.")
	}

	return nil
}

func runTerminalStream(encoder *fountain.Encoder, frames []string, fileName string, size int, duration time.Duration, fps int, sigChan chan os.Signal) error {
	delay := time.Duration(1000/fps) * time.Millisecond
	var timer <-chan time.Time
	if duration > 0 {
		timer = time.After(duration)
	}

	frameIndex := 0
	for {
		select {
		case <-timer:
			fmt.Println("\nSession time expired. Terminating.")
			return nil
		case <-sigChan:
			fmt.Println("\nSession stopped.")
			return nil
		default:
			frameData := frames[frameIndex%len(frames)]
			frameIndex++

			ansi, err := qr.RenderANSI(frameData, qrcode.Low)
			if err == nil {
				fmt.Print("\033[H\033[2J") // Clear terminal screen
				fmt.Printf("Dove Optical Stream | File: %s (%d bytes) | Frame: %d | FPS: %d\n", filepath.Base(fileName), size, frameIndex, fps)
				fmt.Println("Scan screen with Dove Android App. Press Ctrl+C to stop.")
				fmt.Println(ansi)
			}
			time.Sleep(delay)
		}
	}
}
