package com.keyloop.scheduler.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AvailabilityResponse(Long technicianId, LocalDate date, List<BusyWindow> busyWindows) {

    public record BusyWindow(LocalDateTime startTime, LocalDateTime endTime) {
    }

    /**
     * One qualified technician's busy windows for a day, used by
     * TechnicianController's aggregate /technicians/availability endpoint
     * (dealershipId+serviceType+date -> every qualified technician's busy
     * windows) so a calendar/slot-picker UI can compute open slots across
     * whichever technician ends up auto-assigned, without the caller
     * needing to know technician ids up front.
     */
    public record TechnicianAvailability(
            Long technicianId, String technicianName, LocalDate date, List<BusyWindow> busyWindows) {
    }
}
