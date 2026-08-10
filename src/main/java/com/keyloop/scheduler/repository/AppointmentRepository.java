package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * spring.jpa.open-in-view is disabled, so the Hibernate session is
     * closed as soon as the repository call returns. vehicle/customer/
     * technician/serviceBay are all lazy @ManyToOne associations, and
     * AppointmentResponse reads fields off every one of them, so the read
     * path must fetch them eagerly here rather than let the controller hit
     * a LazyInitializationException outside the session.
     */
    @Query("""
            select a from Appointment a
            join fetch a.vehicle
            join fetch a.customer
            join fetch a.technician
            join fetch a.serviceBay
            where a.id = :id
            """)
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    /**
     * Standard half-open interval overlap test: two ranges [startA, endA) and
     * [startB, endB) overlap iff startA < endB and startB < endA. Using
     * strict inequalities means a slot ending exactly when another begins
     * (back-to-back) does NOT count as an overlap.
     */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.technician.id = :technicianId
              and a.status = :status
              and a.startTime < :end
              and :start < a.endTime
            """)
    boolean existsOverlappingForTechnician(
            @Param("technicianId") Long technicianId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") AppointmentStatus status);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.serviceBay.id = :serviceBayId
              and a.status = :status
              and a.startTime < :end
              and :start < a.endTime
            """)
    boolean existsOverlappingForServiceBay(
            @Param("serviceBayId") Long serviceBayId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") AppointmentStatus status);

    @Query("""
            select a from Appointment a
            where a.technician.id = :technicianId
              and a.status = :status
              and a.startTime < :end
              and :start < a.endTime
            order by a.startTime
            """)
    List<Appointment> findOverlappingForTechnician(
            @Param("technicianId") Long technicianId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") AppointmentStatus status);
}
