package com.example.seats.repository;

import com.example.seats.dao.UserShowUsageDao;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Persistence API for the per-user, per-show seat limit. */
@Repository
public class UserQuotaRepository {
    private final UserShowUsageDao usageDao;

    public UserQuotaRepository(UserShowUsageDao usageDao) {
        this.usageDao = usageDao;
    }

    /** Atomically takes {@code n} seats of the user's quota; false if that would exceed {@code limit}. */
    public boolean tryAcquire(UUID showId, String userId, int n, int limit) {
        return usageDao.tryIncrement(showId, userId, n, limit);
    }

    public void lock(UUID showId, String userId) {
        usageDao.lock(showId, userId);
    }

    public void release(UUID showId, String userId, int count) {
        usageDao.decrement(showId, userId, count);
    }
}
