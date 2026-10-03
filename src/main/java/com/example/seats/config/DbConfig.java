package com.example.seats.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the connection pool from DATABASE_URL. Hosting platforms hand out
 * "postgres://user:pass@host:5432/db" URLs; JDBC wants "jdbc:postgresql://host:5432/db".
 */
@Configuration
public class DbConfig {

    public record DbSettings(String jdbcUrl, String user, String password) {}

    static DbSettings parse(String raw) {
        if (raw.startsWith("jdbc:")) {
            return new DbSettings(raw, null, null);
        }
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String user = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (parts.length > 1) {
                password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return new DbSettings("jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query,
                user, password);
    }

    @Bean
    public DbSettings dbSettings(@Value("${app.database-url}") String url) {
        return parse(url);
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(DbSettings s, @Value("${app.db-pool-max}") int poolMax) {
        HikariConfig c = new HikariConfig();
        c.setPoolName("seats");
        c.setJdbcUrl(s.jdbcUrl());
        if (s.user() != null) c.setUsername(s.user());
        if (s.password() != null) c.setPassword(s.password());
        c.setMaximumPoolSize(poolMax);
        c.setMinimumIdle(Math.min(5, poolMax));
        // Requests queue for a connection during an on-sale stampede; don't 503 them after 30s.
        c.setConnectionTimeout(90_000);
        // Start even if the DB is not reachable yet (cold start); /readyz reports the truth.
        c.setInitializationFailTimeout(-1);
        return new HikariDataSource(c);
    }
}
