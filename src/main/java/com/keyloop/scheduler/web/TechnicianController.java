package com.keyloop.scheduler.web;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.Technician;
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

        return new AvailabilityResponse(id, date, busyWindowsFor(id, date));
    }

    /**
     * Aggregate view for a calendar/slot-picker UI: every technician
     * qualified for serviceType at dealershipId, with their busy windows
     * for the day, so the caller can compute open slots without knowing
     * technician ids in advance (they may also leave technicianId blank on
     * POST /appointments and let the backend auto-assign).
     */
    @GetMapping("/availability")
    public List<AvailabilityResponse.TechnicianAvailability> availabilityForService(
            @RequestParam Long dealershipId,
            @RequestParam String serviceType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Technician> qualified = technicianRepository.findBySpecialtyAndDealershipIdOrderById(serviceType, dealershipId);
        return qualified.stream()
                .map(t -> new AvailabilityResponse.TechnicianAvailability(
                        t.getId(), t.getName(), date, busyWindowsFor(t.getId(), date)))
                .toList();
    }

    private List<AvailabilityResponse.BusyWindow> busyWindowsFor(Long technicianId, LocalDate date) {
        List<Appointment> dayAppointments = appointmentRepository.findOverlappingForTechnician(
                technicianId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), AppointmentStatus.CONFIRMED);
        return dayAppointments.stream()
                .map(a -> new AvailabilityResponse.BusyWindow(a.getStartTime(), a.getEndTime()))
                .toList();
    }
}
