package com.checkout.payment.gateway.controller.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Merchant request payload to submit a card payment. */
@Schema(description = "Card payment submission request")
public record PostPaymentRequest(
    @Schema(
        description = "14 to 19 numeric digits",
        example = "2222405343248877",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 14,
        maxLength = 19,
        pattern = "^[0-9]{14,19}$")
    @JsonProperty("card_number")
    String cardNumber,

    @Schema(
        description = "Card expiry month (1-12)",
        example = "12",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minimum = "1",
        maximum = "12")
    @JsonProperty("expiry_month")
    Integer expiryMonth,

    @Schema(
        description = "Card expiry year; month and year must not be in the past",
        example = "2028",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("expiry_year")
    Integer expiryYear,

    @Schema(
        description = "Supported 3-character ISO 4217 currency code",
        example = "GBP",
        requiredMode = Schema.RequiredMode.REQUIRED,
        allowableValues = {"GBP", "USD", "EUR"})
    @JsonProperty("currency")
    String currency,

    @Schema(
        description = "Positive integer amount in minor currency units (e.g., 1050 for 10.50)",
        example = "1050",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minimum = "1")
    @JsonProperty("amount")
    Integer amount,

    @Schema(
        description = "3 or 4 numeric digits",
        example = "123",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 3,
        maxLength = 4,
        pattern = "^[0-9]{3,4}$")
    @JsonProperty("cvv")
    String cvv) {

  @Override
  public String toString() {
    return "PostPaymentRequest{"
        + "cardNumber=REDACTED"
        + ", expiryMonth=" + expiryMonth
        + ", expiryYear=" + expiryYear
        + ", currency='" + currency + '\''
        + ", amount=" + amount
        + ", cvv=REDACTED"
        + '}';
  }
}
