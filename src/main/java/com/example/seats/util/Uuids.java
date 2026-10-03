package com.example.seats.util;

import java.util.UUID;

public final class Uuids {
    private Uuids() {}

    /** Parses a UUID, returning null (instead of throwing) for null or malformed input. */
    public static UUID tryParse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
