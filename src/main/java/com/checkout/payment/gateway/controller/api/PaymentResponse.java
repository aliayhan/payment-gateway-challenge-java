package com.checkout.payment.gateway.controller.api;

import com.checkout.payment.gateway.model.enums.PaymentStatus;
import com.checkout.payment.gateway.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Merchant-facing masked payment details returned on submission and lookup. */
@Schema(description = "Masked payment details")
public record PaymentResponse(
    @Schema(
        description = "Gateway payment identifier",
        example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(
        description = "Final acquiring bank outcome",
        example = "Authorized",
        allowableValues = {"Authorized", "Declined"})
    PaymentStatus status,

    @Schema(
        description = "Last four digits of the card number, preserving leading zeros",
        example = "0042")
    String cardNumberLastFour,

    @Schema(description = "Card expiry month (1-12)", example = "12")
    int expiryMonth,

    @Schema(description = "Card expiry year", example = "2028")
    int expiryYear,

    @Schema(description = "3-character ISO currency code", example = "GBP")
    String currency,

    @Schema(description = "Positive amount in minor currency units", example = "1050")
    int amount) {

  /** Maps a stored {@link Payment} record into an API response DTO. */
  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.status(),
        payment.cardNumberLastFour(),
        payment.expiryMonth(),
        payment.expiryYear(),
        payment.currency(),
        payment.amount());
  }
}
