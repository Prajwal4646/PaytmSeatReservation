package com.example.seats.controller;

import com.example.seats.dao.DbProbe;
import com.example.seats.filter.RequestIdFilter;
import com.example.seats.model.SeatCount;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DbProbe probe;
    private final PrometheusMeterRegistry registry;

    public HealthController(DbProbe probe, PrometheusMeterRegistry registry) {
        this.probe = probe;
        this.registry = registry;
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return Map.of("status", "ok");
    }

    /** Readiness actually touches the database and fails closed (503). */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyz() {
        if (probe.ready()) {
            return ResponseEntity.ok(Map.of("status", "ready"));
        }
        log.warn("readiness check failed");
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "not_ready");
        error.put("message", "database unreachable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", error, "request_id", String.valueOf(MDC.get(RequestIdFilter.MDC_KEY))));
    }

    @GetMapping(value = "/metrics", produces = "text/plain;version=0.0.4;charset=utf-8")
    public String metricsEndpoint() {
        StringBuilder sb = new StringBuilder(registry.scrape());
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        // seat gauges are read from the DB at scrape time, so they can never drift from API state
        try {
            Map<String, Map<String, Long>> byShow = new TreeMap<>();
            for (SeatCount c : probe.seatCounts()) {
                byShow.computeIfAbsent(c.showId(), k -> new LinkedHashMap<>()).put(c.status(), c.n());
            }
            for (String status : List.of("available", "held", "confirmed")) {
                String name = "seats_" + status;
                sb.append("# HELP ").append(name).append(" Seats currently ").append(status).append(", per show\n");
                sb.append("# TYPE ").append(name).append(" gauge\n");
                for (Map.Entry<String, Map<String, Long>> e : byShow.entrySet()) {
                    sb.append(name).append("{show_id=\"").append(e.getKey()).append("\"} ")
                            .append(e.getValue().getOrDefault(status, 0L)).append('\n');
                }
            }
            sb.append("# HELP db_up 1 if the database answered the scrape\n# TYPE db_up gauge\ndb_up 1\n");
        } catch (Exception e) {
            sb.append("# TYPE db_up gauge\ndb_up 0\n");
        }
        return sb.toString();
    }
}
