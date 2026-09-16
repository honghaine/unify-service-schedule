package com.keyloop.scheduler.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PayRequest {
    private String orderId;
    private Double amount;
}
