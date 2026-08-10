package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
