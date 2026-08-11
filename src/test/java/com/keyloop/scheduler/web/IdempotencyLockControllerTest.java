package com.keyloop.scheduler.web;

import com.keyloop.scheduler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Idempotency-Key lock is a UX/dedup layer in front of BookingService,
 * not a replacement for the MySQL-level correctness guarantee (see
 * BookingServiceConcurrencyTest for that one). Deliberately uses five
 * *non-overlapping* time windows for the same technician — MySQL's overlap
 * lock would happily confirm all five on its own, so if this test observes
 * only one 201, that can only be explained by the shared Idempotency-Key
 * rejecting the other four before they ever reach BookingService.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdempotencyLockControllerTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentSubmitsWithSameIdempotencyKey_onlyOneReaches201() throws Exception {
        LocalDateTime baseHour = LocalDateTime.now().plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0);
        String idempotencyKey = "test-key-" + System.nanoTime();
        int attempts = 5;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attempts; i++) {
            LocalDateTime start = baseHour.plusHours(i);
            LocalDateTime end = start.plusHours(1);
            String requestJson = createRequestJson(1L, "OIL_CHANGE", 1L, start, end);
            executor.submit(() -> {
                try {
                    startSignal.await();
                    int responseStatus = mockMvc.perform(post("/appointments")
                                    .contentType("application/json")
                                    .header("Idempotency-Key", idempotencyKey)
                                    .content(requestJson))
                            .andReturn().getResponse().getStatus();
                    if (responseStatus == 201) {
                        created.incrementAndGet();
                    } else if (responseStatus == 409) {
                        conflicted.incrementAndGet();
                    } else {
                        unexpected.add(new AssertionError("unexpected status " + responseStatus));
                    }
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        startSignal.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(created.get()).isEqualTo(1);
        assertThat(conflicted.get()).isEqualTo(attempts - 1);
    }

    @Test
    void submitWithoutIdempotencyKey_behavesAsBefore() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(11).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(3L, "OIL_CHANGE", 2L, start, end)))
                .andExpect(status().isCreated());
    }

    private String createRequestJson(Long vehicleId, String serviceType, Long dealershipId, LocalDateTime start, LocalDateTime end) {
        return """
                {"vehicleId":%d,"serviceType":"%s","dealershipId":%d,"desiredStart":"%s","desiredEnd":"%s"}
                """.formatted(vehicleId, serviceType, dealershipId, ISO.format(start), ISO.format(end));
    }
}
