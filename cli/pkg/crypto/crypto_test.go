package crypto

import (
	"bytes"
	"testing"
)

func TestEncryptDecrypt(t *testing.T) {
	key, err := GenerateKey()
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	message := []byte("Air-gapped zero-network transmission payload")
	ciphertext, err := Encrypt(message, key)
	if err != nil {
		t.Fatalf("encryption failed: %v", err)
	}

	decrypted, err := Decrypt(ciphertext, key)
	if err != nil {
		t.Fatalf("decryption failed: %v", err)
	}

	if !bytes.Equal(decrypted, message) {
		t.Fatalf("decrypted text does not match original message")
	}

	// Corrupt key test
	badKey, _ := GenerateKey()
	_, err = Decrypt(ciphertext, badKey)
	if err == nil {
		t.Fatalf("expected error when decrypting with bad key, got nil")
	}
}

func TestPinDerivation(t *testing.T) {
	salt := []byte("dove-salt-test")
	key1 := DeriveKeyFromPin("123456", salt)
	key2 := DeriveKeyFromPin("123456", salt)
	key3 := DeriveKeyFromPin("654321", salt)

	if !bytes.Equal(key1, key2) {
		t.Fatalf("expected deterministic key derivation for identical pin")
	}
	if bytes.Equal(key1, key3) {
		t.Fatalf("expected different keys for different pins")
	}
}
