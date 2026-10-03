package com.example.seats.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation id + access log. The id comes from an incoming X-Request-ID (sanitised) or is
 * generated. It is put in the logging MDC (so every log line of this request carries it), echoed
 * back in the X-Request-ID response header, and included in error bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "request_id";
    private static final Logger access = LoggerFactory.getLogger("app.access");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader("X-Request-ID");
        String rid = incoming == null ? "" : incoming.replaceAll("[^A-Za-z0-9_.-]", "");
        if (rid.length() > 64) rid = rid.substring(0, 64);
        if (rid.isEmpty()) rid = UUID.randomUUID().toString().replace("-", "");

        MDC.put(MDC_KEY, rid);
        res.setHeader("X-Request-ID", rid);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            String path = req.getRequestURI();
            if (!path.equals("/metrics") && !path.equals("/healthz")) {
                access.atInfo().setMessage("request")
                        .addKeyValue("method", req.getMethod())
                        .addKeyValue("path", path)
                        .addKeyValue("status", res.getStatus())
                        .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000.0)
                        .log();
            }
            MDC.remove(MDC_KEY);
        }
    }
}
