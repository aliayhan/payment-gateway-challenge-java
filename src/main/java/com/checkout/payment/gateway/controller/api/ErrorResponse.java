package com.checkout.payment.gateway.controller.api;

import com.checkout.payment.gateway.model.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Consistent, safe error payload returned by the gateway. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Gateway error response")
public record ErrorResponse(
    @Schema(description = "Present as Rejected when payment input is invalid", example = "Rejected")
    PaymentStatus status,

    @Schema(description = "Safe error summary", example = "Payment request validation failed")
    String message) {

  public ErrorResponse(String message) {
    this(null, message);
  }

  public static ErrorResponse rejected(String message) {
    return new ErrorResponse(PaymentStatus.REJECTED, message);
  }
}
