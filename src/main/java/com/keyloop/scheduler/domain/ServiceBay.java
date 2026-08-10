package com.keyloop.scheduler.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_bay")
public class ServiceBay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long dealershipId;

    private String bayNumber;

    protected ServiceBay() {
    }

    public ServiceBay(Long dealershipId, String bayNumber) {
        this.dealershipId = dealershipId;
        this.bayNumber = bayNumber;
    }

    public Long getId() {
        return id;
    }

    public Long getDealershipId() {
        return dealershipId;
    }

    public String getBayNumber() {
        return bayNumber;
    }
}
