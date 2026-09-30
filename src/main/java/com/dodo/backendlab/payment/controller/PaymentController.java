package com.dodo.backendlab.payment.controller;

import com.dodo.backendlab.payment.domain.Payment;
import com.dodo.backendlab.payment.dto.PaymentRequest;
import com.dodo.backendlab.payment.dto.PaymentResponse;
import com.dodo.backendlab.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        Payment payment = paymentService.pay(
                idempotencyKey,
                request.amount()
        );

        return ResponseEntity.ok(
                PaymentResponse.from(payment)
        );
    }
}