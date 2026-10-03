package com.example.seats.model;

/** Outcome of a cancel call. {@code changed} is false when the reservation was already cancelled. */
public record CancelResult(Reservation reservation, boolean changed) {
}
