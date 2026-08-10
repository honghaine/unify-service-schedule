package com.keyloop.scheduler.web.dto;

import com.keyloop.scheduler.domain.Appointment;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long vehicleId,
        String vehicleVin,
        Long customerId,
        Long technicianId,
        String technicianName,
        Long serviceBayId,
        String bayNumber,
        String serviceType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(),
                appointment.getCustomer().getId(),
                appointment.getTechnician().getId(),
                appointment.getTechnician().getName(),
                appointment.getServiceBay().getId(),
                appointment.getServiceBay().getBayNumber(),
                appointment.getServiceType(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus().name());
    }
}
