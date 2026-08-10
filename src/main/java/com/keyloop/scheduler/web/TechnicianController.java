package com.keyloop.scheduler.web;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.exception.ResourceNotFoundException;
import com.keyloop.scheduler.repository.AppointmentRepository;
import com.keyloop.scheduler.repository.TechnicianRepository;
import com.keyloop.scheduler.web.dto.AvailabilityResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/technicians")
public class TechnicianController {

    private final TechnicianRepository technicianRepository;
    private final AppointmentRepository appointmentRepository;

    public TechnicianController(TechnicianRepository technicianRepository, AppointmentRepository appointmentRepository) {
        this.technicianRepository = technicianRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!technicianRepository.existsById(id)) {
            throw new ResourceNotFoundException("Technician %d not found".formatted(id));
        }

        List<Appointment> dayAppointments = appointmentRepository.findOverlappingForTechnician(
                id, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), AppointmentStatus.CONFIRMED);

        List<AvailabilityResponse.BusyWindow> busyWindows = dayAppointments.stream()
                .map(a -> new AvailabilityResponse.BusyWindow(a.getStartTime(), a.getEndTime()))
                .toList();

        return new AvailabilityResponse(id, date, busyWindows);
    }
}
