package com.keyloop.scheduler.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Exposes booking.attempts.total and booking.conflicts.total so the
 * business-relevant "conflict rate" (conflicts / attempts) can be computed
 * in Prometheus/Grafana rather than baked into the app as a single ratio.
 */
@Component
public class BookingMetrics {

    private final Counter attempts;
    private final Counter conflicts;

    public BookingMetrics(MeterRegistry registry) {
        this.attempts = Counter.builder("booking.attempts.total")
                .description("Total booking attempts received")
                .register(registry);
        this.conflicts = Counter.builder("booking.conflicts.total")
                .description("Booking attempts that failed with 409 due to no availability")
                .register(registry);
    }

    public void recordAttempt() {
        attempts.increment();
    }

    public void recordConflict() {
        conflicts.increment();
    }
}
