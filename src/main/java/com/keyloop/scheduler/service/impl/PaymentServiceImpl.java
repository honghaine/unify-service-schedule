package com.keyloop.scheduler.service.impl;

import com.keyloop.scheduler.domain.PaymentEvent;
import com.keyloop.scheduler.dto.request.PayRequest;
import com.keyloop.scheduler.repository.PaymentEventRepository;
import com.keyloop.scheduler.service.PaymentEventPublisher;
import com.keyloop.scheduler.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @Override
    public void pay(PayRequest request) {
        // Fast-path idempotency check: Antom retries the webhook on anything
        // but a prompt 200, so the same orderId can arrive more than once.
        if (paymentEventRepository.findByOrderId(request.getOrderId()).isPresent()) {
            return;
        }

        PaymentEvent event;
        try {
            // save() commits on its own (SimpleJpaRepository.save is
            // @Transactional), independent of whatever happens next, so the
            // row survives even if the publish step below fails or the
            // process crashes right after this line.
            event = paymentEventRepository.save(
                    new PaymentEvent(request.getOrderId(), BigDecimal.valueOf(request.getAmount())));
        } catch (DataIntegrityViolationException e) {
            // unique constraint on order_id caught a concurrent duplicate
            // webhook that slipped past the check above — safe no-op.
            return;
        }

        paymentEventPublisher.publish(event);
    }
}
