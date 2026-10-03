package com.example.seats.repository;

import com.example.seats.dao.IdempotencyKeyDao;
import com.example.seats.model.IdempotencyRecord;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Persistence API for idempotency keys. */
@Repository
public class IdempotencyRepository {
    private final IdempotencyKeyDao keyDao;

    public IdempotencyRepository(IdempotencyKeyDao keyDao) {
        this.keyDao = keyDao;
    }

    /** True if this call claimed the key; false if the key already exists (a retry or a duplicate). */
    public boolean claim(String userId, String key, String requestHash, UUID reservationId) {
        return keyDao.insertIfAbsent(userId, key, requestHash, reservationId);
    }

    public Optional<IdempotencyRecord> find(String userId, String key) {
        return keyDao.find(userId, key);
    }
}
