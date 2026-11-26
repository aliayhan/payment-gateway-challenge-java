package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.model.enums.PaymentStatus;
import java.util.UUID;

/** Immutable masked payment record persisted by the gateway. */
public record Payment(
    UUID id,
    PaymentStatus status,
    String cardNumberLastFour,
    int expiryMonth,
    int expiryYear,
    String currency,
    int amount) {
}
