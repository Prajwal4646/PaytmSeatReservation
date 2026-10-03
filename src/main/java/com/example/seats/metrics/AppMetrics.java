package com.example.seats.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics. Micrometer's Prometheus registry exposes these as:
 *   reservations_confirmed_total, seats_sold_total, reservations_cancelled_total,
 *   reservations_declined_total{reason=...}
 */
@Component
public class AppMetrics {
    private final MeterRegistry registry;
    public final Counter confirmed;
    public final Counter seatsSold;
    public final Counter cancelled;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.confirmed = Counter.builder("reservations.confirmed")
                .description("Reservations confirmed (one per reservation, not per seat)")
                .register(registry);
        this.seatsSold = Counter.builder("seats.sold")
                .description("Seats confirmed across all reservations").register(registry);
        this.cancelled = Counter.builder("reservations.cancelled")
                .description("Reservations cancelled by their owner").register(registry);
        // every reason is visible at 0 so dashboards and reconciliation have no gaps
        for (String r : new String[]{"seat_taken", "per_user_limit", "idempotent_replay",
                "idempotency_key_reuse"}) {
            declined(r, 0);
        }
    }

    public void declined(String reason) {
        declined(reason, 1);
    }

    private void declined(String reason, double amount) {
        Counter c = Counter.builder("reservations.declined")
                .description("Reservation attempts declined, by reason")
                .tag("reason", reason).register(registry);
        if (amount > 0) c.increment(amount);
    }
}
