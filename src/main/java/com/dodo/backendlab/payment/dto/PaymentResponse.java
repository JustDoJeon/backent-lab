package com.dodo.backendlab.payment.dto;

import com.dodo.backendlab.payment.domain.Payment;
import com.dodo.backendlab.payment.domain.PaymentStatus;

public record PaymentResponse(
        Long paymentId,
        String idempotencyKey,
        Long amount,
        PaymentStatus status
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getIdempotencyKey(),
                payment.getAmount(),
                payment.getStatus()
        );
    }
}