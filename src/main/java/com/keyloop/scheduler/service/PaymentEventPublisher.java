package com.keyloop.scheduler.service;

import com.keyloop.scheduler.domain.PaymentEvent;
import com.keyloop.scheduler.kafka.producer.KafkaProducerService;
import com.keyloop.scheduler.repository.PaymentEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Shared publish step for the payment outbox: attempt the Kafka send and
 * mark the row PUBLISHED only on success. Used both right after a webhook
 * persists its row (PaymentServiceImpl) and by the retry job that sweeps up
 * rows left PENDING by a crash (PaymentOutboxPublisher) — so a failed send
 * always just leaves the row PENDING for the next attempt instead of losing
 * the event.
 */
@Service
public class PaymentEventPublisher {

    @Value("${kafka.topic.name1:payment-events}")
    private String topicName;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    public void publish(PaymentEvent event) {
        try {
            kafkaProducerService.sendMessage(topicName, event.getOrderId(), event.getAmount().toString());
            event.markPublished();
            paymentEventRepository.save(event);
        } catch (Exception e) {
            // broker unreachable / send failed — row stays PENDING, retried next pass
        }
    }
}
