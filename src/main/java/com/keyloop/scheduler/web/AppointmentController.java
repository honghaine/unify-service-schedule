package com.keyloop.scheduler.web;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.exception.ResourceNotFoundException;
import com.keyloop.scheduler.repository.AppointmentRepository;
import com.keyloop.scheduler.service.BookAppointmentCommand;
import com.keyloop.scheduler.service.BookingService;
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

    public AppointmentController(BookingService bookingService, AppointmentRepository appointmentRepository) {
        this.bookingService = bookingService;
        this.appointmentRepository = appointmentRepository;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        Appointment appointment = bookingService.bookAppointment(new BookAppointmentCommand(
                request.vehicleId(),
                request.serviceType(),
                request.dealershipId(),
                request.desiredStart(),
                request.desiredEnd()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
    }

    @GetMapping("/{id}")
    public AppointmentResponse getById(@PathVariable Long id) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment %d not found".formatted(id)));
        return AppointmentResponse.from(appointment);
    }
}
