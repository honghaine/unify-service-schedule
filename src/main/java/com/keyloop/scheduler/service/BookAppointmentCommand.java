package com.keyloop.scheduler.service;

import java.time.LocalDateTime;

/**
 * Either vehicleId identifies an existing vehicle, or the guest fields
 * (customerName/customerEmail/customerPhone/vehicleVin/vehicleMake/
 * vehicleModel) describe a customer+vehicle to find-or-create — enforced by
 * CreateAppointmentRequest.isVehicleIdentificationValid(). technicianId is
 * an optional explicit choice; null means auto-assign.
 */
public record BookAppointmentCommand(
        Long vehicleId,
        String serviceType,
        Long dealershipId,
        LocalDateTime desiredStart,
        LocalDateTime desiredEnd,
        Long technicianId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String vehicleVin,
        String vehicleMake,
        String vehicleModel) {
}
