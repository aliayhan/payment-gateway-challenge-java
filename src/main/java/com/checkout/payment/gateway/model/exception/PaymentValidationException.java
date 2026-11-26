package com.checkout.payment.gateway.model.exception;

import com.checkout.payment.gateway.validation.PaymentRequestError;
import java.util.List;

/** Thrown when a merchant payment request fails gateway validation before calling the bank. */
public class PaymentValidationException extends RuntimeException {

  private final List<PaymentRequestError> errors;

  public PaymentValidationException(List<PaymentRequestError> errors) {
    super("Payment request validation failed");
    this.errors = List.copyOf(errors);
  }

  public List<PaymentRequestError> getErrors() {
    return errors;
  }
}
