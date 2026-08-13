package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.TestcontainersConfiguration;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.Customer;
import com.keyloop.scheduler.domain.ServiceBay;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the overlap-detection queries directly against a real MySQL
 * container (via Testcontainers) so the edge cases around interval
 * boundaries are verified against the same SQL the booking service runs in
 * production, not a hand-rolled Java re-implementation of the predicate.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AppointmentRepositoryOverlapTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private Technician technician;
    private ServiceBay serviceBay;

    private static final LocalDateTime EXISTING_START = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime EXISTING_END = LocalDateTime.of(2026, 1, 1, 11, 0);

    @BeforeEach
    void setUp() {
        // dealershipId=999 / bayNumber="TEST-BAY" deliberately fall outside the
        // Flyway seed data (V2__seed_data.sql) range so this test's fixtures
        // never collide with the unique constraint on (dealership_id, bay_number).
        Customer customer = entityManager.persist(new Customer("Test Customer", "test@example.com", "555-0100"));
        Vehicle vehicle = entityManager.persist(new Vehicle("1HGCM82633A000001", "Honda", "Civic", customer));
        technician = entityManager.persist(new Technician("Test Tech", "OIL_CHANGE", 999L));
        serviceBay = entityManager.persist(new ServiceBay(999L, "TEST-BAY"));

        Appointment existing = new Appointment(
                vehicle, customer, technician, serviceBay, "OIL_CHANGE",
                EXISTING_START, EXISTING_END, AppointmentStatus.CONFIRMED);
        entityManager.persist(existing);
        entityManager.flush();
    }

    @Test
    void exactMatchOverlaps() {
        assertThat(overlapsTechnician(EXISTING_START, EXISTING_END)).isTrue();
    }

    @Test
    void partialOverlapAtStartOverlaps() {
        // 09:30 - 10:30 overlaps the 10:00-11:00 existing appointment.
        assertThat(overlapsTechnician(EXISTING_START.minusMinutes(30), EXISTING_START.plusMinutes(30))).isTrue();
    }

    @Test
    void partialOverlapAtEndOverlaps() {
        // 10:30 - 11:30 overlaps the 10:00-11:00 existing appointment.
        assertThat(overlapsTechnician(EXISTING_END.minusMinutes(30), EXISTING_END.plusMinutes(30))).isTrue();
    }

    @Test
    void containedWithinExistingOverlaps() {
        // 10:15 - 10:45 is fully inside the existing 10:00-11:00 window.
        assertThat(overlapsTechnician(EXISTING_START.plusMinutes(15), EXISTING_END.minusMinutes(15))).isTrue();
    }

    @Test
    void containingExistingOverlaps() {
        // 09:00 - 12:00 fully contains the existing 10:00-11:00 window.
        assertThat(overlapsTechnician(EXISTING_START.minusHours(1), EXISTING_END.plusHours(1))).isTrue();
    }

    @Test
    void adjacentImmediatelyBeforeDoesNotOverlap() {
        // 09:00 - 10:00 ends exactly when the existing appointment starts.
        assertThat(overlapsTechnician(EXISTING_START.minusHours(1), EXISTING_START)).isFalse();
    }

    @Test
    void adjacentImmediatelyAfterDoesNotOverlap() {
        // 11:00 - 12:00 starts exactly when the existing appointment ends.
        assertThat(overlapsTechnician(EXISTING_END, EXISTING_END.plusHours(1))).isFalse();
    }

    @Test
    void disjointWindowDoesNotOverlap() {
        assertThat(overlapsTechnician(EXISTING_START.plusHours(3), EXISTING_END.plusHours(4))).isFalse();
    }

    @Test
    void cancelledAppointmentsAreIgnored() {
        boolean overlapsAgainstConfirmedOnly = appointmentRepository.existsOverlappingForTechnician(
                technician.getId(), EXISTING_START, EXISTING_END, AppointmentStatus.CANCELLED);
        assertThat(overlapsAgainstConfirmedOnly).isFalse();
    }

    @Test
    void serviceBayOverlapUsesSameSemantics() {
        boolean overlap = appointmentRepository.existsOverlappingForServiceBay(
                serviceBay.getId(), EXISTING_START.plusMinutes(30), EXISTING_END.plusMinutes(30), AppointmentStatus.CONFIRMED);
        boolean noOverlap = appointmentRepository.existsOverlappingForServiceBay(
                serviceBay.getId(), EXISTING_END, EXISTING_END.plusHours(1), AppointmentStatus.CONFIRMED);

        assertThat(overlap).isTrue();
        assertThat(noOverlap).isFalse();
    }

    private boolean overlapsTechnician(LocalDateTime start, LocalDateTime end) {
        return appointmentRepository.existsOverlappingForTechnician(
                technician.getId(), start, end, AppointmentStatus.CONFIRMED);
    }
}
