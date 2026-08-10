package com.keyloop.scheduler.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AvailabilityResponse(Long technicianId, LocalDate date, List<BusyWindow> busyWindows) {

    public record BusyWindow(LocalDateTime startTime, LocalDateTime endTime) {
    }
}
