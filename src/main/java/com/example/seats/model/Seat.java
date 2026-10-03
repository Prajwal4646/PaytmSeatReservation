package com.example.seats.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One seat of a show: its label and current status. */
public record Seat(@JsonProperty("seat") String label, String status) {
    public static final String AVAILABLE = "available";
    public static final String HELD = "held";
    public static final String CONFIRMED = "confirmed";
}
