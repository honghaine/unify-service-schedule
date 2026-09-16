package com.keyloop.scheduler.controller;


import com.keyloop.scheduler.dto.request.PayRequest;
import com.keyloop.scheduler.dto.response.CommonResponse;
import com.keyloop.scheduler.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/pay")
    public CommonResponse pay(@RequestBody PayRequest request) {
        paymentService.pay(request);
        return new CommonResponse("200", "success");
    }

}
