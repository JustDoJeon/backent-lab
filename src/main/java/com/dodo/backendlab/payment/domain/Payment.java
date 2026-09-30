package com.dodo.backendlab.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Table(name = "payment",uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_idempotency_key",
        columnNames = "idempotency_key")
})
@Entity @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    public boolean hasSameAmount(Long requestedAmount) {
       return Objects.equals(this.amount, requestedAmount);
    }

    public static Payment create(String idempotencyKey, Long amount) {
        Payment payment = new Payment();
        payment.idempotencyKey = idempotencyKey;
        payment.amount = amount;
        payment.status = PaymentStatus.COMPLETED;
        return payment;
    }
}
