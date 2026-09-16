package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.domain.PaymentEvent;
import com.keyloop.scheduler.domain.PaymentEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByOrderId(String orderId);

    List<PaymentEvent> findByStatus(PaymentEventStatus status);
}
