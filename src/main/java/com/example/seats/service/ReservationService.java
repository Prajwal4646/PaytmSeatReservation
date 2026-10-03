package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.CancelResult;
import com.example.seats.model.IdempotencyRecord;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.model.Seat;
import com.example.seats.model.Show;
import com.example.seats.repository.IdempotencyRepository;
import com.example.seats.repository.ReservationRepository;
import com.example.seats.repository.SeatRepository;
import com.example.seats.repository.ShowRepository;
import com.example.seats.repository.UserQuotaRepository;
import com.example.seats.util.Uuids;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic. This is where seats are decided.
 *
 * Concurrency model (see WRITEUP.md for the long version):
 *  - Everything for one request runs in ONE Postgres transaction (READ COMMITTED), see TxRunner.
 *  - Seats are claimed with SELECT ... ORDER BY label FOR UPDATE: row locks taken in a
 *    deterministic (sorted) order, so two multi-seat requests can't deadlock each other, and the
 *    loser of a race re-reads the COMMITTED row after the winner finishes and sees it is taken.
 *  - The per-user limit is a single conditional upsert on user_show_usage.
 *  - Idempotency is an INSERT ... ON CONFLICT DO NOTHING on (user_id, key).
 *
 * Global lock order, used by every code path:
 *   idempotency key row -> user_show_usage row -> reservation row -> seat rows (sorted)
 */
@Service
public class ReservationService {
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserQuotaRepository quotaRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final TxRunner tx;

    public ReservationService(ShowRepository showRepository,
                              SeatRepository seatRepository,
                              ReservationRepository reservationRepository,
                              UserQuotaRepository quotaRepository,
                              IdempotencyRepository idempotencyRepository,
                              TxRunner tx) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.quotaRepository = quotaRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ reserve

    /**
     * All-or-nothing: if any requested seat is unavailable, nothing is taken.
     * Throws ApiException for every decline (which rolls the transaction back).
     */
    public ReserveResult reserve(String showIdText, String userId, List<String> seats, String key) {
        UUID showId = Uuids.tryParse(showIdText);
        if (showId == null) throw showNotFound();

        List<String> sorted = new ArrayList<>(seats);
        Collections.sort(sorted);
        String reqHash = requestHash(showId, sorted);
        UUID reservationId = UUID.randomUUID();
        int n = seats.size();

        return tx.inTransaction(() -> {
            Show show = showRepository.findById(showId).orElseThrow(ReservationService::showNotFound);

            // 1) idempotency. The PK (user_id, key) makes a second concurrent insert BLOCK until
            //    the first transaction commits or rolls back.
            if (!idempotencyRepository.claim(userId, key, reqHash, reservationId)) {
                IdempotencyRecord prior = idempotencyRepository.find(userId, key)
                        .orElseThrow(() -> new IllegalStateException("idempotency key vanished"));
                if (!reqHash.equals(prior.requestHash())) {
                    throw ApiException.idempotencyKeyReuse();
                }
                Reservation original = reservationRepository.findById(prior.reservationId())
                        .orElseThrow(() -> new IllegalStateException("idempotent reservation missing"));
                return new ReserveResult(original, true);
            }

            // 2) per-user limit: one conditional upsert, atomic by itself.
            int limit = show.perUserLimit();
            if (n > limit) throw ApiException.perUserLimit(limit);
            if (!quotaRepository.tryAcquire(showId, userId, n, limit)) {
                throw ApiException.perUserLimit(limit);
            }

            // 3) lock the seats in sorted order, then decide.
            List<Seat> locked = seatRepository.lockByLabels(showId, sorted);
            if (locked.size() != n) {
                List<String> known = locked.stream().map(Seat::label).toList();
                List<String> unknown = sorted.stream().filter(s -> !known.contains(s)).toList();
                throw new ApiException(422, "unknown_seat", "some seats do not exist in this show",
                        null, Map.of("seats", unknown));
            }
            List<String> taken = locked.stream()
                    .filter(s -> !Seat.AVAILABLE.equals(s.status()))
                    .map(Seat::label)
                    .toList();
            if (!taken.isEmpty()) throw ApiException.seatTaken(taken);

            // guarded update: defence in depth, only flips seats that are still available
            int updated = seatRepository.confirm(showId, reservationId, sorted);
            if (updated != n) { // cannot happen while we hold the locks
                throw ApiException.seatTaken(List.of());
            }

            Reservation reservation = new Reservation(reservationId, showId, userId, seats,
                    show.pricePaise() * n, Reservation.CONFIRMED);
            reservationRepository.save(reservation);
            return new ReserveResult(reservation, false);
        });
    }

    // ------------------------------------------------------------------- cancel

    public Reservation getReservation(String reservationIdText, String userId) {
        UUID rid = Uuids.tryParse(reservationIdText);
        if (rid == null) throw reservationNotFound();
        Reservation r = reservationRepository.findById(rid).orElseThrow(ReservationService::reservationNotFound);
        if (!userId.equals(r.userId())) {
            throw new ApiException(403, "forbidden", "this reservation belongs to another user");
        }
        return r;
    }

    /** Owner-only release. Idempotent. Never touches a seat that someone else now owns. */
    public CancelResult cancel(String reservationIdText, String userId) {
        UUID rid = Uuids.tryParse(reservationIdText);
        if (rid == null) throw reservationNotFound();

        return tx.inTransaction(() -> {
            // owner / show never change, so reading them without a lock is safe
            Reservation head = reservationRepository.findById(rid).orElseThrow(ReservationService::reservationNotFound);
            if (!userId.equals(head.userId())) {
                throw new ApiException(403, "forbidden", "only the owner can cancel a reservation");
            }
            UUID showId = head.showId();

            // lock order: usage row -> reservation row -> seat rows (same as reserve)
            quotaRepository.lock(showId, userId);
            Reservation res = reservationRepository.findByIdForUpdate(rid)
                    .orElseThrow(ReservationService::reservationNotFound);
            if (Reservation.CANCELLED.equals(res.status())) {
                return new CancelResult(res, false); // idempotent
            }

            // lock only seats that are STILL ours, in sorted order, then free them
            seatRepository.lockOwnedBy(showId, rid);
            int freed = seatRepository.releaseAll(showId, rid);
            quotaRepository.release(showId, userId, freed);
            reservationRepository.markCancelled(rid);

            return new CancelResult(res.withStatus(Reservation.CANCELLED), true);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static ApiException showNotFound() {
        return new ApiException(404, "show_not_found", "show not found");
    }

    private static ApiException reservationNotFound() {
        return new ApiException(404, "reservation_not_found", "reservation not found");
    }

    private static String requestHash(UUID showId, List<String> sortedSeats) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((showId + "|" + String.join(",", sortedSeats)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
