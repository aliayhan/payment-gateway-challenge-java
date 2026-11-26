package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.client.api.BankPaymentRequest;
import com.checkout.payment.gateway.configuration.ApplicationConfiguration;
import com.checkout.payment.gateway.configuration.BankClientProperties;
import com.checkout.payment.gateway.configuration.observability.RequestLoggingFilter;
import com.checkout.payment.gateway.model.exception.BankUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Tag("integration")
class HttpAcquiringBankClientTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private HttpAcquiringBankClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    client = new HttpAcquiringBankClient(
        restTemplate,
        new BankClientProperties("http://localhost:8080", 10_000L, 10_000L));
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void sendsExpectedJsonAndCorrelationHeaderAndReturnsAuthorized() {
    MDC.put(RequestLoggingFilter.CORRELATION_ID, "req-123");
    server.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(RequestLoggingFilter.CORRELATION_ID_HEADER, "req-123"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.card_number").value("2222405343248877"))
        .andExpect(jsonPath("$.expiry_date").value("04/2028"))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.cvv").value("012"))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentDecision decision = client.submitPayment(
        new BankPaymentRequest("2222405343248877", "04/2028", "GBP", 100, "012"));

    assertThat(decision).isEqualTo(BankPaymentDecision.AUTHORIZED);
    server.verify();
  }

  @Test
  void returnsDeclinedWhenAuthorizedIsFalse() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess(
            "{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentDecision decision = client.submitPayment(sampleRequest());

    assertThat(decision).isEqualTo(BankPaymentDecision.DECLINED);
    server.verify();
  }

  @Test
  void maps503ToBankUnavailableException() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withServiceUnavailable());

    assertThatThrownBy(() -> client.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();
  }

  @Test
  void mapsUnexpectedHttpStatusToBankUnavailableException() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error_message\":\"missing field\"}"));

    assertThatThrownBy(() -> client.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();
  }

  @Test
  void mapsSocketTimeoutToBankUnavailableException() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(req -> {
          throw new ResourceAccessException("Read timed out", new SocketTimeoutException("timeout"));
        });

    assertThatThrownBy(() -> client.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();
  }

  @Test
  void mapsConnectExceptionToBankUnavailableException() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(req -> {
          throw new ResourceAccessException("Connection refused", new ConnectException("refused"));
        });

    BankPaymentRequest request = sampleRequest();
    assertThat(request.toString())
        .contains("cardNumber=REDACTED")
        .contains("cvv=REDACTED")
        .doesNotContain("2222405343248872")
        .doesNotContain("123");

    assertThatThrownBy(() -> client.submitPayment(request))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();
  }

  @Test
  void handlesBaseUrlWithTrailingSlash() {
    HttpAcquiringBankClient trailingSlashClient = new HttpAcquiringBankClient(
        restTemplate,
        new BankClientProperties("http://localhost:8080/", 10_000L, 10_000L));
    server.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"abc\"}",
            MediaType.APPLICATION_JSON));

    assertThat(trailingSlashClient.submitPayment(sampleRequest()))
        .isEqualTo(BankPaymentDecision.AUTHORIZED);
    server.verify();
  }

  @Test
  void mapsNonJsonOrMalformed200ResponseToBankUnavailableException() {
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess("<html>upstream proxy error</html>", MediaType.TEXT_HTML));

    assertThatThrownBy(() -> client.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();

    server.reset();
    server.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    server.verify();
  }

  @Test
  void realHttpServerProcessesAuthorizedResponse() throws IOException {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedCorrelationId = new AtomicReference<>();
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/payments", exchange -> {
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      capturedCorrelationId.set(exchange.getRequestHeaders().getFirst(
          RequestLoggingFilter.CORRELATION_ID_HEADER));
      byte[] responseBytes = "{\"authorized\":true,\"authorization_code\":\"real-http\"}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, responseBytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(responseBytes);
      }
    });
    httpServer.start();

    try {
      int port = httpServer.getAddress().getPort();
      BankClientProperties props = new BankClientProperties("http://127.0.0.1:" + port, 500L, 500L);
      RestTemplate realRestTemplate = new ApplicationConfiguration()
          .restTemplate(new RestTemplateBuilder(), props);
      HttpAcquiringBankClient realClient = new HttpAcquiringBankClient(realRestTemplate, props);

      MDC.put(RequestLoggingFilter.CORRELATION_ID, "real-req-1");
      BankPaymentDecision decision = realClient.submitPayment(
          new BankPaymentRequest("2222405343248877", "04/2028", "GBP", 100, "012"));

      assertThat(decision).isEqualTo(BankPaymentDecision.AUTHORIZED);
      assertThat(capturedCorrelationId.get()).isEqualTo("real-req-1");
      assertThat(capturedBody.get()).contains("\"expiry_date\":\"04/2028\"");
    } finally {
      httpServer.stop(0);
    }
  }

  @Test
  void realHttpServerReadTimeoutMapsToBankUnavailableException() throws IOException {
    CountDownLatch releaseHandler = new CountDownLatch(1);
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/payments", exchange -> {
      try {
        releaseHandler.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    httpServer.start();

    try {
      int port = httpServer.getAddress().getPort();
      BankClientProperties props = new BankClientProperties("http://127.0.0.1:" + port, 500L, 100L);
      RestTemplate realRestTemplate = new ApplicationConfiguration()
          .restTemplate(new RestTemplateBuilder(), props);
      HttpAcquiringBankClient realClient = new HttpAcquiringBankClient(realRestTemplate, props);

      assertThatThrownBy(() -> realClient.submitPayment(sampleRequest()))
          .isInstanceOf(BankUnavailableException.class);
    } finally {
      releaseHandler.countDown();
      httpServer.stop(0);
    }
  }

  @Test
  void realHttpClosedPortMapsToBankUnavailableException() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }

    BankClientProperties props = new BankClientProperties(
        "http://127.0.0.1:" + closedPort, 500L, 500L);
    RestTemplate realRestTemplate = new ApplicationConfiguration()
        .restTemplate(new RestTemplateBuilder(), props);
    HttpAcquiringBankClient realClient = new HttpAcquiringBankClient(realRestTemplate, props);

    assertThatThrownBy(() -> realClient.submitPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  private static BankPaymentRequest sampleRequest() {
    return new BankPaymentRequest("2222405343248872", "04/2028", "GBP", 100, "123");
  }
}
