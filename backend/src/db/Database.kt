package backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

data class DbConfig(val url: String, val user: String, val password: String) {
    companion object {
        fun fromEnv() = DbConfig(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5433/malls",
            user = System.getenv("DB_USER") ?: "malls",
            password = System.getenv("DB_PASSWORD") ?: "malls",
        )
    }
}

object Database {
    /** Creates a pooled DataSource and applies pending Flyway migrations. */
    fun connect(config: DbConfig): HikariDataSource {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 8
        })
        migrate(ds)
        return ds
    }

    fun migrate(ds: DataSource) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
    }
}
