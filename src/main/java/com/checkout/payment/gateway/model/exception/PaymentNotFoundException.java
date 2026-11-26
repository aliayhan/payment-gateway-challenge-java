package com.checkout.payment.gateway.model.exception;

import java.util.UUID;

/** Thrown when a payment lookup finds no record for the requested UUID. */
public class PaymentNotFoundException extends RuntimeException {

  private final UUID paymentId;

  public PaymentNotFoundException(UUID paymentId) {
    super("Payment not found");
    this.paymentId = paymentId;
  }

  public UUID getPaymentId() {
    return paymentId;
  }
}
