package com.checkout.payment.gateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the outbound acquiring bank HTTP client. */
@ConfigurationProperties(prefix = "bank.client")
public record BankClientProperties(
    String baseUrl,
    long connectTimeoutMs,
    long readTimeoutMs) {
}
