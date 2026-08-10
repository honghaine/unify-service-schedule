package com.keyloop.scheduler.service;

import java.time.LocalDateTime;

public record BookAppointmentCommand(
        Long vehicleId,
        String serviceType,
        Long dealershipId,
        LocalDateTime desiredStart,
        LocalDateTime desiredEnd) {
}
