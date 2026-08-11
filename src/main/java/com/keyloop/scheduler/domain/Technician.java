package com.keyloop.scheduler.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician")
public class Technician implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String specialty;

    private Long dealershipId;

    protected Technician() {
    }

    public Technician(String name, String specialty, Long dealershipId) {
        this.name = name;
        this.specialty = specialty;
        this.dealershipId = dealershipId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public Long getDealershipId() {
        return dealershipId;
    }
}
