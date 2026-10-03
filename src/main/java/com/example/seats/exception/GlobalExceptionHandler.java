package com.example.seats.exception;

import com.example.seats.filter.RequestIdFilter;
import com.example.seats.metrics.AppMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every error leaves as {"error": {code, message, ...}, "request_id": ...}. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AppMetrics metrics;

    public GlobalExceptionHandler(AppMetrics metrics) {
        this.metrics = metrics;
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String code, String message,
                                                            Map<String, Object> extra) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.putAll(extra);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", error);
        out.put("request_id", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(out);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        if (e.reason() != null) metrics.declined(e.reason());
        return body(e.status(), e.code(), e.getMessage(), e.extra());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(400, "invalid_json", "request body must be valid JSON", Map.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badParam(Exception e) {
        return body(400, "invalid_parameter", "invalid request parameter", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        return body(404, "not_found", "not found", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return body(405, "method_not_allowed", "method not allowed", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException e) {
        return body(415, "unsupported_media_type", "unsupported media type", Map.of());
    }

    /** Database trouble (down, pool exhausted, timeout) is a 503, not an anonymous 500. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<Map<String, Object>> dependency(DataAccessException e) {
        log.error("database unavailable", e);
        return body(HttpStatus.SERVICE_UNAVAILABLE.value(), "dependency_unavailable",
                "database unavailable, retry shortly", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unhandled(Exception e) {
        log.error("unhandled exception", e);
        return body(500, "internal_error", "internal error", Map.of());
    }
}
