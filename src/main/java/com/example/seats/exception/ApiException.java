package com.example.seats.exception;

import java.util.List;
import java.util.Map;

/** A domain outcome that maps to a 4xx response (never a 5xx, except the explicit 503 "busy"). */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    /** Label for the "declined by reason" metric; null if this isn't a reservation decline. */
    private final String reason;
    private final Map<String, Object> extra;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null, Map.of());
    }

    public ApiException(int status, String code, String message, String reason, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.reason = reason;
        this.extra = extra;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String reason() { return reason; }
    public Map<String, Object> extra() { return extra; }

    public static ApiException seatTaken(List<String> seats) {
        return new ApiException(409, "seat_taken", "one or more requested seats are already taken",
                "seat_taken", Map.of("seats", seats));
    }

    public static ApiException perUserLimit(int limit) {
        return new ApiException(409, "per_user_limit",
                "you can hold at most " + limit + " seats for this show",
                "per_user_limit", Map.of("limit", limit));
    }

    public static ApiException idempotencyKeyReuse() {
        return new ApiException(409, "idempotency_key_reuse",
                "this idempotency key was already used with a different request",
                "idempotency_key_reuse", Map.of());
    }
}
