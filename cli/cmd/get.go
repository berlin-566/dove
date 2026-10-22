package cmd

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/berlin-566/dove/pkg/crypto"
	"github.com/berlin-566/dove/pkg/server"
)

type ReceivedMetadata struct {
	SessionID uint64 `json:"session_id"`
	FileName  string `json:"file_name"`
	FileSize  int64  `json:"file_size"`
	HasPin    bool   `json:"has_pin"`
}

// RunGet executes the 'dove get' command logic.
func RunGet(args []string) error {
	outputDir := "."
	for i := 0; i < len(args); i++ {
		if args[i] == "--output" && i+1 < len(args) {
			outputDir = args[i+1]
			i++
		}
	}

	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return fmt.Errorf("failed to create output directory '%s': %w", outputDir, err)
	}

	webDir, err := server.LocateWebDir()
	if err != nil {
		return fmt.Errorf("web directory not found: %w", err)
	}

	port, err := server.FindAvailablePort()
	if err != nil {
		return fmt.Errorf("failed to allocate port: %w", err)
	}

	var sessionRand [8]byte
	_, _ = rand.Read(sessionRand[:])
	sessionID := binary.BigEndian.Uint64(sessionRand[:]) & 0x7FFFFFFFFFFFFFFF

	sessionsDir := filepath.Join(webDir, "sessions")
	_ = os.MkdirAll(sessionsDir, 0755)

	targetFile := filepath.Join(sessionsDir, fmt.Sprintf("%d_received.bin", sessionID))
	metaFile := filepath.Join(sessionsDir, fmt.Sprintf("%d_meta.json", sessionID))
	defer func() {
		_ = os.Remove(targetFile)
		_ = os.Remove(metaFile)
	}()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	srv, err := server.Start(ctx, port, webDir)
	if err != nil {
		return fmt.Errorf("failed to start local PHP server: %w", err)
	}
	defer srv.Stop()

	targetURL := fmt.Sprintf("http://127.0.0.1:%d/scanner.html?session=%d", port, sessionID)
	_ = server.OpenBrowser(targetURL)

	fmt.Println("Dove: Optical Receiver Active")
	fmt.Printf("Session ID: %d\n", sessionID)
	fmt.Printf("Scanner URL: %s\n", targetURL)
	fmt.Println("Status: Centered camera viewport opened in browser. Point your phone screen at the webcam.")
	fmt.Println("Press Ctrl+C to cancel.")

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-sigChan:
			fmt.Println("\nReception cancelled by user.")
			return nil
		case <-ticker.C:
			if _, err := os.Stat(targetFile); err == nil {
				// File payload has arrived!
				rawPayload, err := os.ReadFile(targetFile)
				if err != nil {
					continue
				}

				var fileName = fmt.Sprintf("received_%d.bin", time.Now().Unix())
				if metaBytes, err := os.ReadFile(metaFile); err == nil {
					var meta ReceivedMetadata
					if err := json.Unmarshal(metaBytes, &meta); err == nil && meta.FileName != "" {
						fileName = meta.FileName
					}
				}

				// Check if payload contains prepended AES key (seamless 32-byte key)
				if len(rawPayload) < 32+12 { // 32 key + 12 nonce
					return fmt.Errorf("received payload is corrupt or too small")
				}

				key := rawPayload[:32]
				encryptedData := rawPayload[32:]

				decryptedData, err := crypto.Decrypt(encryptedData, key)
				if err != nil {
					return fmt.Errorf("decryption failed: %w", err)
				}

				// Unpack embedded metadata header if present
				if len(decryptedData) > 2 {
					nameLen := int(binary.BigEndian.Uint16(decryptedData[:2]))
					if nameLen > 0 && 2+nameLen <= len(decryptedData) {
						embeddedName := string(decryptedData[2 : 2+nameLen])
						if !strings.ContainsAny(embeddedName, "/\\:\x00") && len(embeddedName) < 255 {
							fileName = embeddedName
							decryptedData = decryptedData[2+nameLen:]
						}
					}
				}

				outPath := filepath.Join(outputDir, fileName)
				if err := os.WriteFile(outPath, decryptedData, 0644); err != nil {
					return fmt.Errorf("failed to save received file to '%s': %w", outPath, err)
				}

				cleanPath, _ := filepath.Abs(outPath)
				fmt.Println("\nTransfer Complete!")
				fmt.Printf("Saved: %s (%d bytes)\n", cleanPath, len(decryptedData))
				return nil
			}
		}
	}
}
