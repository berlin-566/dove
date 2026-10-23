package fountain

import (
	"bytes"
	"crypto/rand"
	"testing"
)

func TestFountainEncodeDecode(t *testing.T) {
	sessionID := uint64(987654321)
	originalData := make([]byte, 1024)
	if _, err := rand.Read(originalData); err != nil {
		t.Fatalf("failed to generate random test data: %v", err)
	}

	chunkSize := 128
	encoder := NewEncoder(sessionID, originalData, chunkSize)
	decoder := NewDecoder(sessionID, int(encoder.TotalChunks), int(encoder.ChunkSize))

	maxAttempts := int(encoder.TotalChunks) * 5
	recovered := false

	for i := 0; i < maxAttempts; i++ {
		sym := encoder.NextSymbol()
		raw := sym.Serialize()

		deserialized, err := Deserialize(raw)
		if err != nil {
			t.Fatalf("deserialization failed: %v", err)
		}

		if decoder.AddSymbol(deserialized) {
			recovered = true
			break
		}
	}

	if !recovered {
		t.Fatalf("failed to recover data within %d symbols", maxAttempts)
	}

	result := decoder.Reconstruct(len(originalData))
	if !bytes.Equal(result, originalData) {
		t.Fatalf("reconstructed data does not match original data")
	}
}

func TestFountainLarge1MB(t *testing.T) {
	sessionID := uint64(123456789)
	oneMB := 1000000
	originalData := make([]byte, oneMB)
	for i := 0; i < oneMB; i++ {
		originalData[i] = byte(i % 251)
	}

	chunkSize := CalculateOptimalChunkSize(oneMB)
	if chunkSize != 380 {
		t.Fatalf("expected chunkSize 380 for 1MB payload, got %d", chunkSize)
	}

	encoder := NewEncoder(sessionID, originalData, chunkSize)
	decoder := NewDecoder(sessionID, int(encoder.TotalChunks), int(encoder.ChunkSize))

	// Simulate systematic phase delivery
	for i := 0; i < int(encoder.TotalChunks); i++ {
		sym := encoder.NextSymbol()
		if decoder.AddSymbol(sym) {
			break
		}
	}

	if !decoder.IsComplete() {
		t.Fatalf("expected complete reconstruction after systematic symbols: %d / %d", decoder.ResolvedCount, decoder.TotalChunks)
	}

	reconstructed := decoder.Reconstruct(oneMB)
	if !bytes.Equal(reconstructed, originalData) {
		t.Fatalf("1MB reconstructed payload does not match original")
	}
}
