package backend

import backend.db.Database
import backend.db.DbConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.postgresql.PostgreSQLContainer

/** One shared PostgreSQL container and one connection pool per test JVM (needs Docker). */
object TestDb {
    private val container = PostgreSQLContainer("postgres:16-alpine").also { it.start() }
    private val ds: HikariDataSource =
        Database.connect(DbConfig(container.jdbcUrl, container.username, container.password))

    /** Resets the schema and re-applies migrations. Tests run sequentially, so sharing the pool is safe. */
    fun freshDataSource(): HikariDataSource {
        ds.connection.use { c ->
            c.createStatement().use { it.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }
        }
        Database.migrate(ds)
        return ds
    }
}
