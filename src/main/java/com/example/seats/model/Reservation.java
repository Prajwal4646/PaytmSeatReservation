package com.example.seats.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** A reservation; also the JSON shape returned by the reserve / get / cancel endpoints. */
public record Reservation(
        @JsonProperty("reservation_id") UUID id,
        @JsonProperty("show_id") UUID showId,
        @JsonProperty("user_id") String userId,
        List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        String status) {
    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED = "cancelled";

    public Reservation withStatus(String newStatus) {
        return new Reservation(id, showId, userId, seats, amountPaise, newStatus);
    }
}
