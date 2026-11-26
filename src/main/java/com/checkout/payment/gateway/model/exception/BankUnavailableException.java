package com.checkout.payment.gateway.model.exception;

/** Thrown when the acquiring bank responds with 503 Service Unavailable. */
public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException(String message) {
    super(message);
  }
}
