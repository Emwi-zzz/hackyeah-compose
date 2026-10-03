package backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 with a random salt. Stored as `pbkdf2$<iterations>$<salt>$<hash>` (base64). */
object PasswordHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return "pbkdf2\$$ITERATIONS\$${b64(salt)}\$${b64(derive(password, salt, ITERATIONS))}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)).encoded

    private fun b64(b: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(b)
}
