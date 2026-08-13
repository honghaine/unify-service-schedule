package com.keyloop.scheduler.service;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.Customer;
import com.keyloop.scheduler.domain.ServiceBay;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.Vehicle;
import com.keyloop.scheduler.exception.BookingConflictException;
import com.keyloop.scheduler.exception.ResourceNotFoundException;
import com.keyloop.scheduler.repository.AppointmentRepository;
import com.keyloop.scheduler.repository.CustomerRepository;
import com.keyloop.scheduler.repository.ServiceBayRepository;
import com.keyloop.scheduler.repository.TechnicianRepository;
import com.keyloop.scheduler.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Books an appointment by finding a qualified, available Technician and a
 * free ServiceBay for the requested window, then persisting the Appointment
 * atomically.
 *
 * <p>Concurrency: each candidate Technician/ServiceBay row is pessimistically
 * locked (SELECT ... FOR UPDATE) before its overlap is checked. The lock is
 * held for the rest of this transaction, so a second transaction racing for
 * the same resource blocks on the lock until the first commits or rolls
 * back, then re-checks overlap against the now-committed state. This is what
 * makes the availability check safe under concurrent requests instead of
 * merely being a check-then-act race.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final TechnicianRepository technicianRepository;
    private final ServiceBayRepository serviceBayRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingMetrics metrics;

    public BookingService(
            VehicleRepository vehicleRepository,
            CustomerRepository customerRepository,
            TechnicianRepository technicianRepository,
            ServiceBayRepository serviceBayRepository,
            AppointmentRepository appointmentRepository,
            BookingMetrics metrics) {
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.technicianRepository = technicianRepository;
        this.serviceBayRepository = serviceBayRepository;
        this.appointmentRepository = appointmentRepository;
        this.metrics = metrics;
    }

    /**
     * READ_COMMITTED (not the MySQL default REPEATABLE_READ) is required
     * here: under REPEATABLE_READ, InnoDB fixes a transaction's
     * consistent-read snapshot at its first plain SELECT (the vehicle
     * lookup below), so the overlap check later in this method would keep
     * reading that stale snapshot even after the FOR UPDATE lock is
     * acquired - every concurrent transaction would see zero existing
     * appointments and all of them would succeed. READ_COMMITTED gives
     * every plain SELECT a fresh read of the latest committed data, which
     * combined with the FOR UPDATE lock is what actually serializes
     * concurrent bookings against the same technician/bay.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment bookAppointment(BookAppointmentCommand command) {
        metrics.recordAttempt();

        Vehicle vehicle = resolveVehicle(command);

        Technician technician = command.technicianId() != null
                ? findRequestedTechnician(command)
                : findAvailableTechnician(command);
        if (technician == null) {
            metrics.recordConflict();
            throw new BookingConflictException(
                    command.technicianId() != null
                            ? "Requested technician %d is not available for the requested window".formatted(command.technicianId())
                            : "No qualified technician available for serviceType=%s dealershipId=%d in the requested window"
                                    .formatted(command.serviceType(), command.dealershipId()));
        }

        ServiceBay serviceBay = findAvailableServiceBay(command);
        if (serviceBay == null) {
            metrics.recordConflict();
            throw new BookingConflictException(
                    "No service bay available for dealershipId=%d in the requested window"
                            .formatted(command.dealershipId()));
        }

        Appointment appointment = new Appointment(
                vehicle,
                vehicle.getCustomer(),
                technician,
                serviceBay,
                command.serviceType(),
                command.desiredStart(),
                command.desiredEnd(),
                AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        log.info(
                "appointment.confirmed id={} technicianId={} serviceBayId={} vehicleId={}",
                saved.getId(), technician.getId(), serviceBay.getId(), vehicle.getId());
        return saved;
    }

    /**
     * Existing vehicleId is used as-is. Otherwise this is a guest booking:
     * find-or-create the Customer by email, then find-or-create the Vehicle
     * by VIN under that customer. Known gap: two concurrent guest bookings
     * with the same brand-new VIN can both miss the findByVin lookup and
     * race on the vin unique constraint at insert time - acceptable for
     * this scenario's scope (not the double-booking guarantee this system
     * exists to protect), but a real production system would need a
     * find-or-create retry/upsert here.
     */
    private Vehicle resolveVehicle(BookAppointmentCommand command) {
        if (command.vehicleId() != null) {
            return vehicleRepository.findById(command.vehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Vehicle %d not found".formatted(command.vehicleId())));
        }

        Customer customer = customerRepository.findByEmail(command.customerEmail())
                .orElseGet(() -> customerRepository.save(
                        new Customer(command.customerName(), command.customerEmail(), command.customerPhone())));

        return vehicleRepository.findByVin(command.vehicleVin())
                .orElseGet(() -> vehicleRepository.save(
                        new Vehicle(command.vehicleVin(), command.vehicleMake(), command.vehicleModel(), customer)));
    }

    /**
     * Explicit technician choice: lock + check availability for exactly
     * that technician (no specialty/qualification re-check here - the
     * caller chose them, matching TechnicianController's listing of
     * qualified technicians). Returns null on conflict, same as
     * findAvailableTechnician, so the caller's conflict handling is shared.
     */
    private Technician findRequestedTechnician(BookAppointmentCommand command) {
        Technician locked = technicianRepository.lockById(command.technicianId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Technician %d not found".formatted(command.technicianId())));
        if (!locked.getDealershipId().equals(command.dealershipId())) {
            throw new ResourceNotFoundException(
                    "Technician %d is not at dealership %d".formatted(command.technicianId(), command.dealershipId()));
        }
        boolean overlap = appointmentRepository.existsOverlappingForTechnician(
                locked.getId(), command.desiredStart(), command.desiredEnd(), AppointmentStatus.CONFIRMED);
        return overlap ? null : locked;
    }

    private Technician findAvailableTechnician(BookAppointmentCommand command) {
        List<Technician> candidates = technicianRepository.findBySpecialtyAndDealershipIdOrderById(
                command.serviceType(), command.dealershipId());

        for (Technician candidate : candidates) {
            Technician locked = technicianRepository.lockById(candidate.getId()).orElseThrow();
            boolean overlap = appointmentRepository.existsOverlappingForTechnician(
                    locked.getId(), command.desiredStart(), command.desiredEnd(), AppointmentStatus.CONFIRMED);
            if (!overlap) {
                return locked;
            }
        }
        return null;
    }

    private ServiceBay findAvailableServiceBay(BookAppointmentCommand command) {
        List<ServiceBay> candidates = serviceBayRepository.findByDealershipIdOrderById(command.dealershipId());

        for (ServiceBay candidate : candidates) {
            ServiceBay locked = serviceBayRepository.lockById(candidate.getId()).orElseThrow();
            boolean overlap = appointmentRepository.existsOverlappingForServiceBay(
                    locked.getId(), command.desiredStart(), command.desiredEnd(), AppointmentStatus.CONFIRMED);
            if (!overlap) {
                return locked;
            }
        }
        return null;
    }
}
