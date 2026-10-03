package com.example.seats.dao;

import com.example.seats.model.Reservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** SQL for the {@code reservations} table. */
@Repository
public class ReservationDao {
    private static final RowMapper<Reservation> MAPPER = (rs, i) -> new Reservation(
            rs.getObject("id", UUID.class),
            rs.getObject("show_id", UUID.class),
            rs.getString("user_id"),
            Arrays.asList((String[]) rs.getArray("seats").getArray()),
            rs.getLong("amount_paise"),
            rs.getString("status"));

    private final JdbcTemplate jdbc;

    public ReservationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Reservation r) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO reservations (id, show_id, user_id, seats, amount_paise, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setObject(1, r.id());
            ps.setObject(2, r.showId());
            ps.setString(3, r.userId());
            ps.setArray(4, JdbcArrays.textArray(con, r.seats()));
            ps.setLong(5, r.amountPaise());
            ps.setString(6, r.status());
            return ps;
        });
    }

    public Optional<Reservation> findById(UUID id) {
        return jdbc.query("SELECT * FROM reservations WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM reservations WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    public void markCancelled(UUID id) {
        jdbc.update("UPDATE reservations SET status = 'cancelled', cancelled_at = now() WHERE id = ?", id);
    }
}
