package com.example.seats.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GET /shows/{id}: the show, live seat counts, and (optionally) the per-seat list. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowDetails(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        long available,
        long held,
        long confirmed,
        List<Seat> seats) {

    /** @param seats null means "do not include the per-seat list" */
    public static ShowDetails of(Show show, Map<String, Long> countsByStatus, List<Seat> seats) {
        return new ShowDetails(show.id(), show.name(), show.pricePaise(), show.perUserLimit(),
                show.totalSeats(),
                countsByStatus.getOrDefault(Seat.AVAILABLE, 0L),
                countsByStatus.getOrDefault(Seat.HELD, 0L),
                countsByStatus.getOrDefault(Seat.CONFIRMED, 0L),
                seats);
    }
}
