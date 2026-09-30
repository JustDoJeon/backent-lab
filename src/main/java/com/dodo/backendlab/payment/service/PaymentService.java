package com.dodo.backendlab.payment.service;

import com.dodo.backendlab.payment.domain.Payment;
import com.dodo.backendlab.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;


    @Transactional
    public Payment pay(String idempotencyKey,Long amount) {
        validateRequest(idempotencyKey,amount);


        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> validateExistingPayment(payment, amount))
                .orElseGet(() -> createPayment(idempotencyKey, amount));
    }

    private Payment validateExistingPayment(Payment payment,Long requestedAmount)
        {
            if (!payment.hasSameAmount(requestedAmount)) {
                throw new IllegalArgumentException(
                        "동일한 멱등성 키에 다른 결제 금액을 사용할 수 없습니다."
                );
            }

            return payment;
        }

    private Payment createPayment(String idempotencyKey, Long amount) {
        Payment payment = Payment.create(idempotencyKey, amount);
        return paymentRepository.save(payment);
    }


    private void validateRequest(String idempotencyKey, Long amount) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등성 키는 필수입니다.");
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
    }
}
