package com.example.seats.repository;

import com.example.seats.dao.SeatDao;
import com.example.seats.model.Seat;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Persistence API for seat inventory changes made by reserve / cancel. */
@Repository
public class SeatRepository {
    private final SeatDao seatDao;

    public SeatRepository(SeatDao seatDao) {
        this.seatDao = seatDao;
    }

    /** Locks the requested seats (sorted order => no deadlocks) and returns the ones that exist. */
    public List<Seat> lockByLabels(UUID showId, List<String> sortedLabels) {
        return seatDao.lockByLabels(showId, sortedLabels);
    }

    /** Marks still-available seats as confirmed for the reservation; returns how many changed. */
    public int confirm(UUID showId, UUID reservationId, List<String> labels) {
        return seatDao.confirm(showId, reservationId, labels);
    }

    /** Locks only the seats that are still owned by this reservation. */
    public void lockOwnedBy(UUID showId, UUID reservationId) {
        seatDao.lockByReservation(showId, reservationId);
    }

    /** Frees every seat still owned by the reservation; returns how many were freed. */
    public int releaseAll(UUID showId, UUID reservationId) {
        return seatDao.releaseByReservation(showId, reservationId);
    }
}
