package com.keyloop.scheduler.cronjob;

import com.keyloop.scheduler.domain.PaymentEvent;
import com.keyloop.scheduler.domain.PaymentEventStatus;
import com.keyloop.scheduler.repository.PaymentEventRepository;
import com.keyloop.scheduler.service.PaymentEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sweeps payment_event rows left PENDING by a crash between "webhook
 * persisted" and "Kafka publish acked" — including a crash of the instance
 * that wrote the row, since the row lives in the DB, not in that process.
 */
@Service
public class PaymentOutboxPublisher {

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @Scheduled(fixedRate = 5000)
    public void retryPendingEvents() {
        for (PaymentEvent event : paymentEventRepository.findByStatus(PaymentEventStatus.PENDING)) {
            paymentEventPublisher.publish(event);
        }
    }
}
