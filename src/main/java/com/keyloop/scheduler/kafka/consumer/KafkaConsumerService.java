package com.keyloop.scheduler.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @KafkaListener(topics = "${kafka.topic.name1:payment-events}", groupId = "group1")
    public void listen(ConsumerRecord<String, String> record) {
        System.out.println("Partition: " + record.partition() +
                ", Offset: " + record.offset() +
                ", Topic: " + record.topic() +
                ", Key: " + record.key() +
                ", Value: " + record.value());
    }
}
