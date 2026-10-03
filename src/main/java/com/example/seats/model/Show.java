package com.example.seats.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** A show (event). Also the JSON shape of one entry in GET /shows. */
public record Show(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats) {
}
