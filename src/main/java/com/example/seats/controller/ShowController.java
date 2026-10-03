package com.example.seats.controller;

import com.example.seats.exception.ApiException;
import com.example.seats.model.ShowDetails;
import com.example.seats.service.AuthService;
import com.example.seats.service.ShowService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ShowController {
    private static final int MAX_SEATS_PER_SHOW = 100_000;

    private final ShowService shows;
    private final AuthService auth;
    private final RequestParser parser;

    public ShowController(ShowService shows, AuthService auth, RequestParser parser) {
        this.shows = shows;
        this.auth = auth;
        this.parser = parser;
    }

    @PostMapping("/shows")
    public ResponseEntity<ShowDetails> createShow(HttpServletRequest req) {
        auth.requireAdmin(req.getHeader("Authorization"));
        JsonNode body = parser.readBody(req);

        JsonNode name = body.get("name");
        if (name == null || !name.isTextual() || name.asText().isBlank() || name.asText().length() > 200) {
            throw new ApiException(422, "invalid_name", "name must be a non-empty string");
        }
        JsonNode price = body.get("price_paise");
        if (!parser.isInt(price) || price.asLong() < 0) {
            throw new ApiException(422, "invalid_price", "price_paise must be a non-negative integer");
        }
        long limit = 4;
        if (body.has("per_user_limit")) {
            JsonNode l = body.get("per_user_limit");
            if (!parser.isInt(l) || l.asLong() < 1 || l.asLong() > Integer.MAX_VALUE) {
                throw new ApiException(422, "invalid_limit", "per_user_limit must be an integer >= 1");
            }
            limit = l.asLong();
        }
        List<String> seats = parser.seatList(body.get("seats"), MAX_SEATS_PER_SHOW);

        ShowDetails show = shows.createShow(name.asText().strip(), seats, price.asLong(), (int) limit);
        return ResponseEntity.status(HttpStatus.CREATED).body(show);
    }

    @GetMapping("/shows")
    public Map<String, Object> listShows() {
        return Map.of("shows", shows.listShows());
    }

    @GetMapping("/shows/{showId}")
    public ShowDetails getShow(@PathVariable String showId,
                               @RequestParam(name = "include_seats", defaultValue = "true")
                               boolean includeSeats) {
        return shows.getShow(showId, includeSeats);
    }
}
