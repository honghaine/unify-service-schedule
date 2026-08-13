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
 * The idempotency lock is a UX/dedup layer in front of BookingService, not
 * a replacement for the MySQL-level correctness guarantee (see
 * BookingServiceConcurrencyTest for that one). Since AppointmentController
 * now derives the lock key from request fields (time/dealership/service/
 * technician/customer) rather than a client-supplied header, "same
 * request" is what makes two submissions collide - these tests fire
 * *identical* bodies concurrently rather than varying a header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdempotencyLockControllerTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentIdenticalGuestSubmits_onlyOneReaches201() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(12).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        String email = "dup-test-" + System.nanoTime() + "@example.com";
        String requestJson = guestRequestJson(email, start, end);

        int attempts = 5;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    int responseStatus = mockMvc.perform(post("/appointments")
                                    .contentType("application/json")
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
    void differentCustomers_sameSlotAndTechnician_bothReachBookingService() throws Exception {
        // Different emails -> different idempotency keys -> neither is
        // rejected by the lock itself. They still can't both win (only one
        // technician/bay), but that's MySQL's overlap lock doing its job,
        // not this one - proves the key is scoped per-customer rather than
        // blocking unrelated customers from each other.
        LocalDateTime start = LocalDateTime.now().plusDays(13).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        String requestA = guestRequestJson("customer-a-" + System.nanoTime() + "@example.com", start, end);
        String requestB = guestRequestJson("customer-b-" + System.nanoTime() + "@example.com", start, end);

        int statusA = mockMvc.perform(post("/appointments").contentType("application/json").content(requestA))
                .andReturn().getResponse().getStatus();
        int statusB = mockMvc.perform(post("/appointments").contentType("application/json").content(requestB))
                .andReturn().getResponse().getStatus();

        assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void submitWithExistingVehicleId_stillWorks() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(14).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content("""
                                {"vehicleId":2,"serviceType":"BRAKES","dealershipId":1,"desiredStart":"%s","desiredEnd":"%s"}
                                """.formatted(ISO.format(start), ISO.format(end))))
                .andExpect(status().isCreated());
    }

    private String guestRequestJson(String email, LocalDateTime start, LocalDateTime end) {
        return """
                {"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"%s","desiredEnd":"%s",
                 "customerName":"Dup Test","customerEmail":"%s","customerPhone":"555-0100",
                 "vehicleVin":"VIN-%s","vehicleMake":"Toyota","vehicleModel":"Corolla"}
                """.formatted(ISO.format(start), ISO.format(end), email, email.hashCode());
    }
}
