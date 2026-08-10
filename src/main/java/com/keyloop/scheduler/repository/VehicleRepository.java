package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
