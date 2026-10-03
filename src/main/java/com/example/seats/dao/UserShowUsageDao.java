package com.example.seats.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** SQL for the {@code user_show_usage} table (how many seats a user currently holds per show). */
@Repository
public class UserShowUsageDao {
    private final JdbcTemplate jdbc;

    public UserShowUsageDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One conditional upsert: adds {@code n} to the user's held count only if the result stays
     * within {@code limit}. Returns false when it would exceed the limit.
     */
    public boolean tryIncrement(UUID showId, String userId, int n, int limit) {
        List<Integer> held = jdbc.queryForList(
                "INSERT INTO user_show_usage (show_id, user_id, held) VALUES (?, ?, ?) "
                        + "ON CONFLICT (show_id, user_id) DO UPDATE "
                        + "SET held = user_show_usage.held + EXCLUDED.held "
                        + "WHERE user_show_usage.held + EXCLUDED.held <= ? "
                        + "RETURNING held", Integer.class, showId, userId, n, limit);
        return !held.isEmpty();
    }

    public void lock(UUID showId, String userId) {
        jdbc.queryForList("SELECT 1 FROM user_show_usage WHERE show_id = ? AND user_id = ? FOR UPDATE",
                showId, userId);
    }

    public void decrement(UUID showId, String userId, int count) {
        jdbc.update("UPDATE user_show_usage SET held = held - ? WHERE show_id = ? AND user_id = ?",
                count, showId, userId);
    }
}
