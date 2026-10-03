package com.example.seats.model;

/** Outcome of a reserve call. {@code replayed} is true when an earlier identical request is returned. */
public record ReserveResult(Reservation reservation, boolean replayed) {
}
