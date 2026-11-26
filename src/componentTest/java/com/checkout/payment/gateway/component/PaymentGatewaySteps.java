package com.checkout.payment.gateway.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Cucumber step definitions exercising the containerized payment gateway over HTTP. */
public class PaymentGatewaySteps {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static String baseUrl;

  private HttpResponse<String> lastResponse;
  private JsonNode lastJson;
  private JsonNode createdPaymentJson;
  private String lastLocation;

  @BeforeAll
  public static void startContainers() {
    baseUrl = ContainerEnvironment.startAndGetGatewayBaseUrl();
  }

  @When("a merchant submits a payment request with:")
  public void merchantSubmitsPaymentRequest(String payload)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/payment"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();

    lastResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    lastJson = parseJsonIfPresent(lastResponse.body());
    lastLocation = lastResponse.headers().firstValue("Location").orElse(null);
    if (lastResponse.statusCode() == 201) {
      createdPaymentJson = lastJson;
    }
  }

  @When("the merchant retrieves the payment using the Location header")
  public void merchantRetrievesPaymentUsingLocationHeader()
      throws IOException, InterruptedException {
    assertThat(lastLocation).isNotBlank();
    sendGet(lastLocation);
  }

  @When("a merchant requests GET {string}")
  public void merchantRequestsGet(String path) throws IOException, InterruptedException {
    sendGet(path);
  }

  @Then("the response status should be {int}")
  public void responseStatusShouldBe(int expectedStatus) {
    assertThat(lastResponse.statusCode()).isEqualTo(expectedStatus);
  }

  @And("the response should include header {string}")
  public void responseShouldIncludeHeader(String headerName) {
    assertThat(lastResponse.headers().firstValue(headerName)).isPresent();
  }

  @And("the response should not include header {string}")
  public void responseShouldNotIncludeHeader(String headerName) {
    assertThat(lastResponse.headers().firstValue(headerName)).isEmpty();
  }

  @And("the response JSON field {string} should be {string}")
  public void responseJsonFieldShouldBeString(String field, String expected) {
    assertThat(lastJson).isNotNull();
    assertThat(lastJson.has(field)).isTrue();
    assertThat(lastJson.get(field).isTextual()).isTrue();
    assertThat(lastJson.get(field).asText()).isEqualTo(expected);
  }

  @And("the response JSON field {string} should be {int}")
  public void responseJsonFieldShouldBeInt(String field, int expected) {
    assertThat(lastJson).isNotNull();
    assertThat(lastJson.has(field)).isTrue();
    assertThat(lastJson.get(field).isInt()).isTrue();
    assertThat(lastJson.get(field).asInt()).isEqualTo(expected);
  }

  @And("the response JSON should match the previously created payment")
  public void responseJsonShouldMatchPreviouslyCreatedPayment() {
    assertThat(lastJson).isEqualTo(createdPaymentJson);
  }

  @And("the response body should contain {string}")
  public void responseBodyShouldContain(String snippet) {
    assertThat(lastResponse.body()).contains(snippet);
  }

  @And("the response body should not contain {string}")
  public void responseBodyShouldNotContain(String secret) {
    assertThat(lastResponse.body()).doesNotContain(secret);
  }

  private void sendGet(String path) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .GET()
        .build();

    lastResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    lastJson = parseJsonIfPresent(lastResponse.body());
  }

  private static JsonNode parseJsonIfPresent(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readTree(body);
    } catch (IOException ex) {
      return null;
    }
  }
}
