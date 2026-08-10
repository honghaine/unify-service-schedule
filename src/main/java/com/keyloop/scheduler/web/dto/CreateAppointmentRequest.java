package com.keyloop.scheduler.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateAppointmentRequest(
        @NotNull Long vehicleId,
        @NotBlank String serviceType,
        @NotNull Long dealershipId,
        @NotNull LocalDateTime desiredStart,
        @NotNull LocalDateTime desiredEnd) {

    @AssertTrue(message = "desiredEnd must be after desiredStart")
    public boolean isWindowValid() {
        return desiredStart == null || desiredEnd == null || desiredEnd.isAfter(desiredStart);
    }
}
