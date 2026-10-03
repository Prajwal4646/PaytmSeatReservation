package com.example.seats.model;

/** Number of seats of one show in one status (used by the /metrics gauges). */
public record SeatCount(String showId, String status, long n) {
}
