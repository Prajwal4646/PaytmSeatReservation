package com.example.seats.dao;

import com.example.seats.config.DbConfig;
import com.example.seats.model.SeatCount;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Health/metrics queries on a short-lived connection with tight timeouts, deliberately NOT through
 * the request pool. Why: during a stampede every pooled connection is busy and requests queue, but
 * a readiness probe or a metrics scrape must still answer quickly, and when the database is down it
 * must fail in ~2s instead of waiting for the pool's (long) connection timeout.
 */
@Component
public class DbProbe {
    private final DbConfig.DbSettings settings;
    private final SchemaInitializer schema;

    public DbProbe(DbConfig.DbSettings settings, SchemaInitializer schema) {
        this.settings = settings;
        this.schema = schema;
    }

    private Connection open() throws SQLException {
        Properties p = new Properties();
        if (settings.user() != null) p.setProperty("user", settings.user());
        if (settings.password() != null) p.setProperty("password", settings.password());
        p.setProperty("connectTimeout", "2");
        p.setProperty("socketTimeout", "5");
        p.setProperty("loginTimeout", "2");
        return DriverManager.getConnection(settings.jdbcUrl(), p);
    }

    /** True only if the schema is initialised AND the database answers right now. */
    public boolean ready() {
        if (!schema.isReady()) return false;
        try (Connection c = open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<SeatCount> seatCounts() throws SQLException {
        List<SeatCount> out = new ArrayList<>();
        try (Connection c = open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT show_id::text, status, count(*) FROM seats GROUP BY show_id, status")) {
            while (rs.next()) {
                out.add(new SeatCount(rs.getString(1), rs.getString(2), rs.getLong(3)));
            }
        }
        return out;
    }
}
