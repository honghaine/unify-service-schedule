package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.config.CacheConfig;
import com.keyloop.scheduler.domain.Technician;
import jakarta.persistence.LockModeType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    @Cacheable(CacheConfig.TECHNICIAN_CANDIDATES_CACHE)
    List<Technician> findBySpecialtyAndDealershipIdOrderById(String specialty, Long dealershipId);

    /**
     * Row lock held for the duration of the enclosing transaction so two
     * concurrent booking attempts against the same technician serialize
     * instead of both passing the overlap check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Technician t where t.id = :id")
    Optional<Technician> lockById(@Param("id") Long id);
}
