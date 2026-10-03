package com.example.seats.dao;

import com.example.seats.model.Show;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL for the {@code shows} table. */
@Repository
public class ShowDao {
    private static final String COLUMNS = "id, name, price_paise, per_user_limit, total_seats";

    private static final RowMapper<Show> MAPPER = (rs, i) -> new Show(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getLong("price_paise"),
            rs.getInt("per_user_limit"),
            rs.getInt("total_seats"));

    private final JdbcTemplate jdbc;

    public ShowDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Show show) {
        jdbc.update("INSERT INTO shows (id, name, price_paise, per_user_limit, total_seats) "
                        + "VALUES (?, ?, ?, ?, ?)",
                show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats());
    }

    public Optional<Show> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shows WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Show> findRecent(int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shows ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
    }
}
