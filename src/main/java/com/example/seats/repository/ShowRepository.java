package com.example.seats.repository;

import com.example.seats.dao.SeatDao;
import com.example.seats.dao.ShowDao;
import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence API for shows (a show and its seat inventory are one aggregate). */
@Repository
public class ShowRepository {
    private final ShowDao showDao;
    private final SeatDao seatDao;

    public ShowRepository(ShowDao showDao, SeatDao seatDao) {
        this.showDao = showDao;
        this.seatDao = seatDao;
    }

    /** Stores the show and creates one 'available' seat per label. Call inside a transaction. */
    public void save(Show show, List<String> seatLabels) {
        showDao.insert(show);
        seatDao.insertAll(show.id(), seatLabels);
    }

    public Optional<Show> findById(UUID id) {
        return showDao.findById(id);
    }

    public List<Show> findRecent(int limit) {
        return showDao.findRecent(limit);
    }

    /** Show + seat counts (+ per-seat list). Call inside one transaction so all parts agree. */
    public Optional<ShowDetails> findDetails(UUID id, boolean includeSeats) {
        return showDao.findById(id).map(show -> ShowDetails.of(
                show,
                seatDao.countByStatus(id),
                includeSeats ? seatDao.findAllByShow(id) : null));
    }
}
