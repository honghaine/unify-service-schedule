package com.keyloop.scheduler.web;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.exception.BookingConflictException;
import com.keyloop.scheduler.exception.ResourceNotFoundException;
import com.keyloop.scheduler.repository.AppointmentRepository;
import com.keyloop.scheduler.service.BookAppointmentCommand;
import com.keyloop.scheduler.service.BookingService;
import com.keyloop.scheduler.service.IdempotencyLockService;
import com.keyloop.scheduler.web.dto.AppointmentResponse;
import com.keyloop.scheduler.web.dto.CreateAppointmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final BookingService bookingService;
    private final AppointmentRepository appointmentRepository;
    private final IdempotencyLockService idempotencyLockService;

    public AppointmentController(
            BookingService bookingService,
            AppointmentRepository appointmentRepository,
            IdempotencyLockService idempotencyLockService) {
        this.bookingService = bookingService;
        this.appointmentRepository = appointmentRepository;
        this.idempotencyLockService = idempotencyLockService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        String idempotencyKey = deriveIdempotencyKey(request);
        IdempotencyLockService.LockAttempt lockAttempt = idempotencyLockService.tryAcquire(idempotencyKey);
        if (lockAttempt.outcome() == IdempotencyLockService.Outcome.REJECTED) {
            throw new BookingConflictException(
                    "Duplicate request in flight for the same slot/technician/customer combination");
        }
        try {
            Appointment appointment = bookingService.bookAppointment(new BookAppointmentCommand(
                    request.vehicleId(),
                    request.serviceType(),
                    request.dealershipId(),
                    request.desiredStart(),
                    request.desiredEnd(),
                    request.technicianId(),
                    request.customerName(),
                    request.customerEmail(),
                    request.customerPhone(),
                    request.vehicleVin(),
                    request.vehicleMake(),
                    request.vehicleModel()));
            return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
        } finally {
            idempotencyLockService.release(lockAttempt);
        }
    }

    /**
     * Natural-key dedupe, not a client-supplied token: desiredStart +
     * desiredEnd + dealershipId + serviceType + technician-or-"any" +
     * customer identity (email, or vehicleId when booking against an
     * existing vehicle without a guest email). This only dedupes exact
     * duplicate submissions (e.g. a double-click) against the same
     * customer - it deliberately never mixes two different customers'
     * requests together, and never blocks two genuinely different time
     * windows even if submitted by the same customer at once.
     */
    private String deriveIdempotencyKey(CreateAppointmentRequest request) {
        String technicianPart = request.technicianId() != null ? request.technicianId().toString() : "any";
        String customerPart = request.customerEmail() != null && !request.customerEmail().isBlank()
                ? request.customerEmail().trim().toLowerCase()
                : "vehicle:" + request.vehicleId();
        return "%s|%s|%d|%s|%s|%s".formatted(
                request.desiredStart(), request.desiredEnd(), request.dealershipId(),
                request.serviceType(), technicianPart, customerPart);
    }

    @GetMapping("/{id}")
    public AppointmentResponse getById(@PathVariable Long id) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment %d not found".formatted(id)));
        return AppointmentResponse.from(appointment);
    }
}
