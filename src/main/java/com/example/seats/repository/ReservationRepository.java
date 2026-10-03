package com.example.seats.repository;

import com.example.seats.dao.ReservationDao;
import com.example.seats.model.Reservation;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Persistence API for reservations. */
@Repository
public class ReservationRepository {
    private final ReservationDao reservationDao;

    public ReservationRepository(ReservationDao reservationDao) {
        this.reservationDao = reservationDao;
    }

    public void save(Reservation reservation) {
        reservationDao.insert(reservation);
    }

    public Optional<Reservation> findById(UUID id) {
        return reservationDao.findById(id);
    }

    /** Same as findById but row-locks the reservation until the transaction ends. */
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return reservationDao.findByIdForUpdate(id);
    }

    public void markCancelled(UUID id) {
        reservationDao.markCancelled(id);
    }
}
