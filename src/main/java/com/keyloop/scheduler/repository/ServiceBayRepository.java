package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.config.CacheConfig;
import com.keyloop.scheduler.domain.ServiceBay;
import jakarta.persistence.LockModeType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceBayRepository extends JpaRepository<ServiceBay, Long> {

    @Cacheable(CacheConfig.SERVICE_BAY_CANDIDATES_CACHE)
    List<ServiceBay> findByDealershipIdOrderById(Long dealershipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ServiceBay b where b.id = :id")
    Optional<ServiceBay> lockById(@Param("id") Long id);
}
