package com.keyloop.scheduler.cronjob;

import com.keyloop.scheduler.kafka.producer.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
public class ScheduledMessage {

    @Value("${kafka.topic.name1:payment-events}")
    private String topicName;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    private static final Random random = new Random();

    @Scheduled(fixedRate = 1000)
    public void sendMessage() {
        kafkaProducerService.sendMessage(topicName,
                String.valueOf(random.nextInt(1, 10000)),
                UUID.randomUUID().toString());
    }

}
