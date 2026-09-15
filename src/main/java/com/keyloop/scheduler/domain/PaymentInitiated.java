package com.keyloop.scheduler.domain;

import lombok.Data;

@Data
public class PaymentInitiated {
    private String paymentId;
    private String orderId;
    private Double paymentAmount;
}
