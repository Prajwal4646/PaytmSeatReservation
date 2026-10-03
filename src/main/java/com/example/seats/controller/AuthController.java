package com.example.seats.controller;

import com.example.seats.exception.ApiException;
import com.example.seats.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;
    private final RequestParser parser;
    private final boolean enableTokenMint;

    public AuthController(AuthService auth, RequestParser parser,
                          @Value("${app.enable-token-mint}") boolean enableTokenMint) {
        this.auth = auth;
        this.parser = parser;
        this.enableTokenMint = enableTokenMint;
    }

    /** Demo-only: mint a user token. Disable with ENABLE_TOKEN_MINT=false. */
    @PostMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> mintToken(HttpServletRequest req) {
        if (!enableTokenMint) throw new ApiException(404, "not_found", "not found");
        JsonNode body = parser.readBody(req);
        JsonNode userId = body.get("user_id");
        if (userId == null || !userId.isTextual() || !AuthService.USER_ID.matcher(userId.asText()).matches()) {
            throw new ApiException(422, "invalid_user_id", "user_id must match [A-Za-z0-9_.@:-]{1,128}");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", auth.makeToken(userId.asText()));
        out.put("user_id", userId.asText());
        out.put("expires_in", AuthService.TOKEN_TTL_SECONDS);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }
}
