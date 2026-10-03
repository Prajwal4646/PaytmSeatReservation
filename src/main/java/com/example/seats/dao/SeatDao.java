package com.example.seats.dao;

import com.example.seats.model.Seat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL for the {@code seats} table. */
@Repository
public class SeatDao {
    private static final RowMapper<Seat> MAPPER =
            (rs, i) -> new Seat(rs.getString("label"), rs.getString("status"));

    private final JdbcTemplate jdbc;

    public SeatDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates one 'available' seat per label; the position in the list becomes the display order. */
    public void insertAll(UUID showId, List<String> labels) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO seats (show_id, label, ord) "
                            + "SELECT ?, label, ord - 1 FROM unnest(?::text[]) WITH ORDINALITY AS t(label, ord)");
            ps.setObject(1, showId);
            ps.setArray(2, JdbcArrays.textArray(con, labels));
            return ps;
        });
    }

    /** SELECT ... FOR UPDATE in label order. Callers must pass labels already sorted. */
    public List<Seat> lockByLabels(UUID showId, List<String> sortedLabels) {
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT label, status FROM seats WHERE show_id = ? AND label = ANY(?) "
                            + "ORDER BY label FOR UPDATE");
            ps.setObject(1, showId);
            ps.setArray(2, JdbcArrays.textArray(con, sortedLabels));
            return ps;
        }, MAPPER);
    }

    /** Guarded update: only flips seats that are still 'available'. Returns the number flipped. */
    public int confirm(UUID showId, UUID reservationId, List<String> labels) {
        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE seats SET status = 'confirmed', reservation_id = ? "
                            + "WHERE show_id = ? AND label = ANY(?) AND status = 'available'");
            ps.setObject(1, reservationId);
            ps.setObject(2, showId);
            ps.setArray(3, JdbcArrays.textArray(con, labels));
            return ps;
        });
    }

    /** Row-locks (in label order) the seats that still belong to this reservation. */
    public void lockByReservation(UUID showId, UUID reservationId) {
        jdbc.queryForList("SELECT label FROM seats WHERE show_id = ? AND reservation_id = ? "
                + "ORDER BY label FOR UPDATE", showId, reservationId);
    }

    /** Returns the seats still owned by this reservation to 'available'; gives the count freed. */
    public int releaseByReservation(UUID showId, UUID reservationId) {
        return jdbc.update("UPDATE seats SET status = 'available', reservation_id = NULL "
                + "WHERE show_id = ? AND reservation_id = ?", showId, reservationId);
    }

    public Map<String, Long> countByStatus(UUID showId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT status, count(*) AS n FROM seats WHERE show_id = ? GROUP BY status", showId)) {
            counts.put((String) r.get("status"), ((Number) r.get("n")).longValue());
        }
        return counts;
    }

    public List<Seat> findAllByShow(UUID showId) {
        return jdbc.query("SELECT label, status FROM seats WHERE show_id = ? ORDER BY ord", MAPPER, showId);
    }
}
