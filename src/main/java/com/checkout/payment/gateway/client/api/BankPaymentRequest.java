package com.checkout.payment.gateway.client.api;

import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Outbound payload sent to the acquiring bank {@code POST /payments} endpoint. */
public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    @JsonProperty("currency") String currency,
    @JsonProperty("amount") int amount,
    @JsonProperty("cvv") String cvv) {

  /** Converts a validated merchant request into the bank simulator payload format. */
  public static BankPaymentRequest from(PostPaymentRequest request) {
    String formattedExpiry = String.format("%02d/%04d", request.expiryMonth(), request.expiryYear());
    return new BankPaymentRequest(
        request.cardNumber(),
        formattedExpiry,
        request.currency(),
        request.amount(),
        request.cvv());
  }

  @Override
  public String toString() {
    return "BankPaymentRequest{"
        + "cardNumber=REDACTED"
        + ", expiryDate='" + expiryDate + '\''
        + ", currency='" + currency + '\''
        + ", amount=" + amount
        + ", cvv=REDACTED"
        + '}';
  }
}
