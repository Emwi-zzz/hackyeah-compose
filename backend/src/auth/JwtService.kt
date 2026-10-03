package backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.security.SecureRandom
import java.util.Date

class JwtService(
    secret: String,
    private val ttlSeconds: Long = 3600,
    private val issuer: String = "hackyeah-backend",
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()
    val expiresInSeconds: Long get() = ttlSeconds

    fun issue(user: User): String = JWT.create()
        .withIssuer(issuer)
        .withSubject(user.id.toString())
        .withClaim("username", user.username)
        .withClaim("role", user.role)
        .withExpiresAt(Date(System.currentTimeMillis() + ttlSeconds * 1000))
        .sign(algorithm)

    companion object {
        fun fromEnv(): JwtService {
            val secret = System.getenv("JWT_SECRET")
            if (secret.isNullOrBlank()) {
                println("WARNING: JWT_SECRET is not set; using a random secret, tokens become invalid after restart")
                val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
                return JwtService(bytes.joinToString("") { "%02x".format(it) }, ttlFromEnv())
            }
            require(secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
            return JwtService(secret, ttlFromEnv())
        }

        private fun ttlFromEnv() = System.getenv("JWT_TTL_SECONDS")?.toLongOrNull() ?: 3600
    }
}
