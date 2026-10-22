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
