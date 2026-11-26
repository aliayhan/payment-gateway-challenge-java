package com.checkout.payment.gateway.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Core Spring configuration for HTTP client and OpenAPI. */
@Configuration
@EnableConfigurationProperties(BankClientProperties.class)
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder, BankClientProperties properties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()))
        .build();
  }

  @Bean
  public OpenAPI paymentGatewayOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Checkout.com Payment Gateway API")
            .version("0.1.0")
            .description("API for processing card payments via an acquiring bank and "
                + "retrieving masked payment details."));
  }
}
