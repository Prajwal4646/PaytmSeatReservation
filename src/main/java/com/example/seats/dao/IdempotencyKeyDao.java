package com.example.seats.dao;

import com.example.seats.model.IdempotencyRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL for the {@code idempotency_keys} table. */
@Repository
public class IdempotencyKeyDao {
    private static final RowMapper<IdempotencyRecord> MAPPER = (rs, i) -> new IdempotencyRecord(
            rs.getString("user_id"),
            rs.getString("key"),
            rs.getString("request_hash"),
            rs.getObject("reservation_id", UUID.class));

    private final JdbcTemplate jdbc;

    public IdempotencyKeyDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * INSERT ... ON CONFLICT DO NOTHING. The PK (user_id, key) makes a second concurrent insert
     * BLOCK until the first transaction commits or rolls back. True if this call inserted the row.
     */
    public boolean insertIfAbsent(String userId, String key, String requestHash, UUID reservationId) {
        List<UUID> inserted = jdbc.queryForList(
                "INSERT INTO idempotency_keys (user_id, key, request_hash, reservation_id) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (user_id, key) DO NOTHING "
                        + "RETURNING reservation_id", UUID.class, userId, key, requestHash, reservationId);
        return !inserted.isEmpty();
    }

    public Optional<IdempotencyRecord> find(String userId, String key) {
        return jdbc.query("SELECT user_id, key, request_hash, reservation_id FROM idempotency_keys "
                + "WHERE user_id = ? AND key = ?", MAPPER, userId, key).stream().findFirst();
    }
}
