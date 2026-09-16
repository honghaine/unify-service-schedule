package com.keyloop.scheduler.service;


import com.keyloop.scheduler.dto.request.PayRequest;

public interface PaymentService {
    void pay(PayRequest request);
}
