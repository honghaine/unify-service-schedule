package com.keyloop.scheduler.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Gates duplicate concurrent POST /appointments submits that share the same
 * client-supplied Idempotency-Key header, before they ever reach
 * BookingService. This is a UX/dedup convenience, not a correctness
 * mechanism — MySQL row locking in BookingService is what actually prevents
 * double-booking. If Redis is unreachable, this fails open (proceeds as if
 * no key were supplied) rather than blocking bookings.
 */
@Service
public class IdempotencyLockService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyLockService.class);
    private static final long LEASE_SECONDS = 30;

    private final RedissonClient redissonClient;

    public IdempotencyLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public enum Outcome { ACQUIRED, REJECTED, SKIPPED }

    public record LockAttempt(Outcome outcome, RLock lock) {}

    public LockAttempt tryAcquire(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new LockAttempt(Outcome.SKIPPED, null);
        }
        try {
            RLock lock = redissonClient.getLock("idempotency:" + idempotencyKey);
            boolean acquired = lock.tryLock(0, LEASE_SECONDS, TimeUnit.SECONDS);
            return acquired ? new LockAttempt(Outcome.ACQUIRED, lock) : new LockAttempt(Outcome.REJECTED, null);
        } catch (Exception e) {
            log.warn("idempotency.lock.unavailable key={} reason={}", idempotencyKey, e.toString());
            return new LockAttempt(Outcome.SKIPPED, null);
        }
    }

    public void release(LockAttempt attempt) {
        if (attempt.outcome() == Outcome.ACQUIRED && attempt.lock().isHeldByCurrentThread()) {
            attempt.lock().unlock();
        }
    }
}
