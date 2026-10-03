package com.example.seats.controller;

import com.example.seats.metrics.AppMetrics;
import com.example.seats.model.CancelResult;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.service.AuthService;
import com.example.seats.service.ReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReservationController {
    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);
    private static final int MAX_SEATS_PER_REQUEST = 100;

    private final ReservationService reservations;
    private final AuthService auth;
    private final AppMetrics metrics;
    private final RequestParser parser;

    public ReservationController(ReservationService reservations, AuthService auth,
                                 AppMetrics metrics, RequestParser parser) {
        this.reservations = reservations;
        this.auth = auth;
        this.metrics = metrics;
        this.parser = parser;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<Reservation> reserve(@PathVariable String showId, HttpServletRequest req) {
        String userId = auth.requireUser(req.getHeader("Authorization")); // identity: token only
        JsonNode body = parser.readBody(req);
        List<String> seats = parser.seatList(body.get("seats"), MAX_SEATS_PER_REQUEST);
        String key = parser.idempotencyKey(req.getHeader("Idempotency-Key"), body);

        // NOTE: any "user_id" in the body is deliberately ignored.
        ReserveResult result = reservations.reserve(showId, userId, seats, key);
        Reservation r = result.reservation();
        if (result.replayed()) {
            metrics.declined("idempotent_replay");
            log.atInfo().setMessage("reservation replayed")
                    .addKeyValue("user_id", userId).addKeyValue("show_id", showId)
                    .addKeyValue("reservation_id", r.id().toString()).log();
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(r);
        }
        metrics.confirmed.increment();
        metrics.seatsSold.increment(seats.size());
        log.atInfo().setMessage("reservation confirmed")
                .addKeyValue("user_id", userId).addKeyValue("show_id", showId)
                .addKeyValue("reservation_id", r.id().toString())
                .addKeyValue("seats", r.seats()).log();
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping("/reservations/{id}")
    public Reservation getReservation(@PathVariable String id, HttpServletRequest req) {
        String userId = auth.requireUser(req.getHeader("Authorization"));
        return reservations.getReservation(id, userId);
    }

    @PostMapping("/reservations/{id}/cancel")
    public Reservation cancel(@PathVariable String id, HttpServletRequest req) {
        String userId = auth.requireUser(req.getHeader("Authorization"));
        CancelResult result = reservations.cancel(id, userId);
        if (result.changed()) {
            metrics.cancelled.increment();
            log.atInfo().setMessage("reservation cancelled")
                    .addKeyValue("user_id", userId).addKeyValue("reservation_id", id).log();
        }
        return result.reservation();
    }
}
