package backend.auth

import javax.sql.DataSource

data class User(val id: Long, val username: String, val role: String, val passwordHash: String)

interface UserRepository {
    /** Returns null if the username is already taken (case-insensitive). */
    fun create(username: String, passwordHash: String, role: String = "USER"): User?
    fun findByUsername(username: String): User?
}

class PostgresUserRepository(private val ds: DataSource) : UserRepository {
    override fun create(username: String, passwordHash: String, role: String): User? = ds.connection.use { c ->
        c.prepareStatement(
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?) " +
                "ON CONFLICT DO NOTHING RETURNING id"
        ).use { ps ->
            ps.setString(1, username); ps.setString(2, passwordHash); ps.setString(3, role)
            ps.executeQuery().use { rs -> if (rs.next()) User(rs.getLong(1), username, role, passwordHash) else null }
        }
    }

    override fun findByUsername(username: String): User? = ds.connection.use { c ->
        c.prepareStatement("SELECT id, username, role, password_hash FROM users WHERE lower(username) = lower(?)").use { ps ->
            ps.setString(1, username)
            ps.executeQuery().use { rs ->
                if (rs.next()) User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)) else null
            }
        }
    }
}
