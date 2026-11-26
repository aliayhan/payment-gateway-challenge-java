package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.client.api.BankPaymentRequest;
import com.checkout.payment.gateway.client.api.BankPaymentResponse;
import com.checkout.payment.gateway.configuration.BankClientProperties;
import com.checkout.payment.gateway.model.exception.BankUnavailableException;
import com.checkout.payment.gateway.configuration.observability.RequestLoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/** HTTP implementation of {@link AcquiringBankClient} using Spring {@link RestTemplate}. */
@Component
public class HttpAcquiringBankClient implements AcquiringBankClient {

  private static final Logger LOG = LoggerFactory.getLogger(HttpAcquiringBankClient.class);
  private static final String PAYMENTS_PATH = "/payments";

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public HttpAcquiringBankClient(RestTemplate restTemplate, BankClientProperties properties) {
    this.restTemplate = restTemplate;
    String baseUrl = properties.baseUrl();
    String normalizedBase = baseUrl.endsWith("/")
        ? baseUrl.substring(0, baseUrl.length() - 1)
        : baseUrl;
    this.paymentsUrl = normalizedBase + PAYMENTS_PATH;
  }

  @Override
  public BankPaymentDecision submitPayment(BankPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(
        RequestLoggingFilter.CORRELATION_ID_HEADER,
        MDC.get(RequestLoggingFilter.CORRELATION_ID));

    HttpEntity<BankPaymentRequest> entity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate.postForEntity(
          paymentsUrl,
          entity,
          BankPaymentResponse.class);

      return response.getBody().authorized()
          ? BankPaymentDecision.AUTHORIZED
          : BankPaymentDecision.DECLINED;
    } catch (HttpStatusCodeException ex) {
      int statusCode = ex.getStatusCode().value();
      if (statusCode == 503) {
        LOG.warn("Bank failure category=bank_503 status=503", ex);
      } else {
        LOG.error("Bank failure category=unexpected_http_status status={}", statusCode, ex);
      }
      throw new BankUnavailableException("Acquiring bank is currently unavailable");
    } catch (Exception ex) {
      LOG.error("Bank failure", ex);
      throw new BankUnavailableException("Acquiring bank is currently unavailable");
    }
  }
}
