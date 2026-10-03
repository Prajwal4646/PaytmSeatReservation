package com.example.seats.model;

import java.util.UUID;

/** A stored (user, key) -> request-hash -> reservation mapping used to replay duplicate requests. */
public record IdempotencyRecord(String userId, String key, String requestHash, UUID reservationId) {
}
