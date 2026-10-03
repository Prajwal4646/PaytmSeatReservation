package com.example.seats.controller;

import com.example.seats.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Request-body parsing and validation shared by the controllers. */
@Component
public class RequestParser {
    private final ObjectMapper mapper;

    public RequestParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode readBody(HttpServletRequest req) {
        JsonNode node;
        try {
            node = mapper.readTree(req.getInputStream());
        } catch (IOException e) {
            throw new ApiException(400, "invalid_json", "request body must be valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new ApiException(400, "invalid_json", "request body must be a JSON object");
        }
        return node;
    }

    public boolean isInt(JsonNode n) {
        return n != null && n.isIntegralNumber() && n.canConvertToLong(); // rejects 12.5, "12", true
    }

    public List<String> seatList(JsonNode n, int max) {
        if (n == null || !n.isArray() || n.isEmpty()) {
            throw new ApiException(422, "invalid_seats", "seats must be a non-empty list of strings");
        }
        if (n.size() > max) {
            throw new ApiException(422, "invalid_seats", "at most " + max + " seats allowed here");
        }
        List<String> out = new ArrayList<>(n.size());
        Set<String> unique = new HashSet<>();
        for (JsonNode e : n) {
            if (!e.isTextual()) {
                throw new ApiException(422, "invalid_seats", "seat labels must be 1-32 char strings");
            }
            String s = e.asText();
            if (s.isBlank() || s.length() > 32 || !s.equals(s.strip())) {
                throw new ApiException(422, "invalid_seats", "seat labels must be 1-32 char strings");
            }
            out.add(s);
            unique.add(s);
        }
        if (unique.size() != out.size()) {
            throw new ApiException(422, "duplicate_seats", "seats must not contain duplicates");
        }
        return out;
    }

    /** Idempotency key from the Idempotency-Key header or the body; both must agree if both are sent. */
    public String idempotencyKey(String headerKey, JsonNode body) {
        JsonNode bodyKeyNode = body.get("idempotency_key");
        String bodyKey = null;
        if (bodyKeyNode != null && !bodyKeyNode.isNull()) {
            if (!bodyKeyNode.isTextual()) {
                throw new ApiException(400, "idempotency_key_required", "idempotency_key must be a string");
            }
            bodyKey = bodyKeyNode.asText();
        }
        if (headerKey != null && bodyKey != null && !headerKey.equals(bodyKey)) {
            throw new ApiException(400, "idempotency_key_conflict", "header and body idempotency keys differ");
        }
        String key = headerKey != null ? headerKey : bodyKey;
        if (key == null || key.isEmpty() || key.length() > 255) {
            throw new ApiException(400, "idempotency_key_required",
                    "an idempotency key (1-255 chars) is required via header or body");
        }
        return key;
    }
}
