package com.keyloop.scheduler.service;

import com.keyloop.scheduler.TestcontainersConfiguration;
import com.keyloop.scheduler.exception.BookingConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single most important test in this project: proves the pessimistic
 * locking strategy in {@link BookingService} actually serializes concurrent
 * booking attempts against the same technician/slot, rather than merely
 * asserting it in prose. Fires N requests for the same seeded technician
 * (dealership 1 has exactly one OIL_CHANGE technician, id=1) and the same
 * time window at once and expects exactly one 201-equivalent success and
 * N-1 409 conflicts.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingServiceConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Test
    void concurrentBookingsForSameTechnicianAndSlot_onlyOneSucceeds() throws InterruptedException {
        int attempts = 8;
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch allThreadsReady = new CountDownLatch(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch allThreadsDone = new CountDownLatch(attempts);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                allThreadsReady.countDown();
                try {
                    startSignal.await();
                    bookingService.bookAppointment(new BookAppointmentCommand(1L, "OIL_CHANGE", 1L, start, end));
                    successCount.incrementAndGet();
                } catch (BookingConflictException expected) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    allThreadsDone.countDown();
                }
            });
        }

        allThreadsReady.await(10, TimeUnit.SECONDS);
        startSignal.countDown();
        boolean finished = allThreadsDone.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("all booking attempts should finish within the timeout").isTrue();
        assertThat(unexpected).as("no unexpected exceptions during concurrent booking").isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(attempts - 1);
    }
}
