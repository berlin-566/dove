package com.dove.app.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val AES_GCM_NONCE_SIZE = 12
    private const val GCM_TAG_LENGTH = 128

    fun decrypt(ciphertextWithNonce: ByteArray, keyBytes: ByteArray): ByteArray {
        require(ciphertextWithNonce.size > AES_GCM_NONCE_SIZE) { "Ciphertext too short" }

        val nonce = ciphertextWithNonce.copyOfRange(0, AES_GCM_NONCE_SIZE)
        val ciphertext = ciphertextWithNonce.copyOfRange(AES_GCM_NONCE_SIZE, ciphertextWithNonce.size)

        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    fun encrypt(plaintext: ByteArray, keyBytes: ByteArray): ByteArray {
        val nonce = ByteArray(AES_GCM_NONCE_SIZE)
        SecureRandom().nextBytes(nonce)

        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }
}
