package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"errors"
	"io"
)

var (
	ErrDecryptionFailed = errors.New("failed to decrypt payload: invalid key or corrupted data")
	ErrInvalidPayload   = errors.New("payload too short for nonce and ciphertext")
)

// GenerateKey produces 32 cryptographically secure random bytes for AES-256.
func GenerateKey() ([]byte, error) {
	key := make([]byte, 32)
	if _, err := io.ReadFull(rand.Reader, key); err != nil {
		return nil, err
	}
	return key, nil
}

// DeriveKeyFromPin expands a user-provided numeric or textual PIN into a 32-byte key.
func DeriveKeyFromPin(pin string, salt []byte) []byte {
	hasher := sha256.New()
	hasher.Write(salt)
	hasher.Write([]byte(pin))
	return hasher.Sum(nil)
}

// Encrypt encrypts plain data using AES-256-GCM with a random 12-byte nonce prepended.
func Encrypt(plaintext, key []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, err
	}

	ciphertext := gcm.Seal(nonce, nonce, plaintext, nil)
	return ciphertext, nil
}

// Decrypt extracts the 12-byte nonce and decrypts AES-256-GCM ciphertext.
func Decrypt(ciphertext, key []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}

	nonceSize := gcm.NonceSize()
	if len(ciphertext) < nonceSize {
		return nil, ErrInvalidPayload
	}

	nonce, actualCiphertext := ciphertext[:nonceSize], ciphertext[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, actualCiphertext, nil)
	if err != nil {
		return nil, ErrDecryptionFailed
	}

	return plaintext, nil
}
