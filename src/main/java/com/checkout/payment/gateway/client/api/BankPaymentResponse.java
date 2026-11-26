package com.checkout.payment.gateway.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Deserialized response payload from the acquiring bank. */
public record BankPaymentResponse(
    @JsonProperty("authorized") boolean authorized,
    @JsonProperty("authorization_code") String authorizationCode) {
}
