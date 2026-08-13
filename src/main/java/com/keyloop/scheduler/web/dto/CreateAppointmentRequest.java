package com.keyloop.scheduler.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * vehicleId identifies an existing vehicle. When absent, the guest fields
 * (customerName, customerEmail, vehicleVin, vehicleMake, vehicleModel) must
 * all be present — the backend finds-or-creates the customer (by email) and
 * vehicle (by VIN) inline. technicianId is optional: null means auto-assign
 * the first qualified, available technician (existing behavior).
 */
public record CreateAppointmentRequest(
        Long vehicleId,
        @NotBlank String serviceType,
        @NotNull Long dealershipId,
        @NotNull LocalDateTime desiredStart,
        @NotNull LocalDateTime desiredEnd,
        Long technicianId,
        String customerName,
        @Email String customerEmail,
        String customerPhone,
        String vehicleVin,
        String vehicleMake,
        String vehicleModel) {

    @AssertTrue(message = "desiredEnd must be after desiredStart")
    public boolean isWindowValid() {
        return desiredStart == null || desiredEnd == null || desiredEnd.isAfter(desiredStart);
    }

    @AssertTrue(message = "either vehicleId, or customerName+customerEmail+vehicleVin+vehicleMake+vehicleModel, must be provided")
    public boolean isVehicleIdentificationValid() {
        if (vehicleId != null) {
            return true;
        }
        return isPresent(customerName) && isPresent(customerEmail)
                && isPresent(vehicleVin) && isPresent(vehicleMake) && isPresent(vehicleModel);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
