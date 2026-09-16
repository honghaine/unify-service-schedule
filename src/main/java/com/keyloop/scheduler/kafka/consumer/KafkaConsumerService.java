package com.keyloop.scheduler.kafka.consumer;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.repository.AppointmentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @Autowired
    AppointmentRepository appointmentRepository; // fake payment table

    @KafkaListener(topics = "${kafka.topic.name1:payment-events}", groupId = "group1")
    public void listen(ConsumerRecord<String, String> record) {
        appointmentRepository.save(new Appointment(record.key(), record.value()));
        System.out.println("Partition: " + record.partition() +
                ", Offset: " + record.offset() +
                ", Topic: " + record.topic() +
                ", Key: " + record.key() +
                ", Value: " + record.value());
    }
}
