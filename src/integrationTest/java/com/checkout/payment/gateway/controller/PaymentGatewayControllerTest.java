package com.checkout.payment.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.configuration.observability.RequestLoggingFilter;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.enums.PaymentStatus;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private RestTemplate restTemplate;

  @SpyBean
  private PaymentsRepository paymentsRepository;

  private MockRestServiceServer bankServer;

  @BeforeEach
  void setUp() {
    bankServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  void whenPaymentWithIdExistsThenCorrectPaymentIsReturned() throws Exception {
    Payment payment = new Payment(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, "4321", 12, 2026, "USD", 10);
    paymentsRepository.save(payment);

    mvc.perform(get("/payment/" + payment.id()))
        .andExpect(status().isOk())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.id").value(payment.id().toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("4321"))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2026))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(10));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("ID not found"))
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  @Test
  void errorBodyIsJsonEvenWhenClientAcceptsOnlyXml() throws Exception {
    mvc.perform(get("/payment/" + UUID.randomUUID()).accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("ID not found"));
  }

  @Test
  void submitAuthorizedPaymentThenRetrieveViaLocationHeaderPreservingLeadingZeros(
      CapturedOutput output) throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header(
            RequestLoggingFilter.CORRELATION_ID_HEADER, "merchant-corr-1"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.card_number").value("2222405343240041"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.expiry_date").value("04/2028"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.currency").value("GBP"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.amount").value(1050))
        .andExpect(MockRestRequestMatchers.jsonPath("$.cvv").value("099"))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"auth-123\"}",
            MediaType.APPLICATION_JSON));

    String requestJson = """
        {
          "card_number": "2222405343240041",
          "expiry_month": 4,
          "expiry_year": 2028,
          "currency": "GBP",
          "amount": 1050,
          "cvv": "099"
        }
        """;

    MvcResult postResult = mvc.perform(post("/payment")
            .header(RequestLoggingFilter.CORRELATION_ID_HEADER, "merchant-corr-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(header().string(RequestLoggingFilter.CORRELATION_ID_HEADER, "merchant-corr-1"))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0041"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2028))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andReturn();

    String location = postResult.getResponse().getHeader("Location");
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value("0041"))
        .andExpect(jsonPath("$.status").value("Authorized"));

    bankServer.verify();
    assertThat(output.getOut())
        .contains("correlationId=merchant-corr-1")
        .doesNotContain("2222405343240041")
        .doesNotContain("099");
  }

  @Test
  void submitDeclinedPaymentThenRetrieveViaLocationHeader() throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess(
            "{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    String requestJson = """
        {
          "card_number": "2222405343240042",
          "expiry_month": 12,
          "expiry_year": 2028,
          "currency": "USD",
          "amount": 2500,
          "cvv": "123"
        }
        """;

    MvcResult postResult = mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0042"))
        .andReturn();

    String location = postResult.getResponse().getHeader("Location");
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0042"));

    bankServer.verify();
  }

  @Test
  void invalidPaymentIsRejectedWithoutCallingBankOrLeakingInputValues(
      CapturedOutput output) throws Exception {
    String requestJson = """
        {
          "card_number": "411111111111BAD",
          "expiry_month": 12,
          "expiry_year": 2029,
          "currency": "JPY",
          "amount": 0,
          "cvv": "99"
        }
        """;

    MvcResult result = mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Payment request validation failed"))
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody)
        .doesNotContain("411111111111BAD")
        .doesNotContain("JPY")
        .doesNotContain("\"99\"");

    bankServer.verify();
    verify(paymentsRepository, never()).save(any());
    assertThat(output.getOut())
        .contains("status=400")
        .doesNotContain("411111111111BAD")
        .doesNotContain("JPY");
  }

  @Test
  void bank503Returns503WithSingleBankCallAndNoSavedPayment(CapturedOutput output)
      throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andRespond(withServiceUnavailable());

    String requestJson = """
        {
          "card_number": "2222405343248870",
          "expiry_month": 12,
          "expiry_year": 2029,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("Acquiring bank is currently unavailable"));

    bankServer.verify();
    verify(paymentsRepository, never()).save(any());
    assertThat(output.getOut())
        .contains("category=bank_503")
        .contains("status=503");
  }

  @Test
  void bankTimeoutReturns503WithSingleBankCallAndNoSavedPayment(CapturedOutput output)
      throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andRespond(req -> {
          throw new ResourceAccessException("Read timed out", new SocketTimeoutException("timeout"));
        });

    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2029,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("Acquiring bank is currently unavailable"));

    bankServer.verify();
    verify(paymentsRepository, never()).save(any());
    assertThat(output.getOut())
        .contains("Bank failure")
        .contains("status=503");
  }

  @Test
  void bankConnectionErrorAndUnexpectedStatusReturn503() throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andRespond(req -> {
          throw new ResourceAccessException("Connection refused", new ConnectException("refused"));
        });

    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2029,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("Acquiring bank is currently unavailable"));

    bankServer.verify();

    bankServer.reset();
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER));

    bankServer.verify();
    verify(paymentsRepository, never()).save(any());
  }

  @Test
  void whenPaymentIdIsNotUuidThen400IsReturnedAndBlankCorrelationHeaderGeneratesUuid()
      throws Exception {
    MvcResult result = mvc.perform(get("/payment/not-a-uuid")
            .header(RequestLoggingFilter.CORRELATION_ID_HEADER, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("Invalid payment ID format; expected a UUID"))
        .andReturn();

    assertThat(result.getResponse().getHeader(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .isNotBlank();
  }

  @Test
  void malformedJsonOrUnparseableFieldReturns400Rejected() throws Exception {
    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-valid-json"))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field type"));

    bankServer.verify();
  }

  @Test
  void unsupportedContentTypeMethodAndAcceptHeadersReturn4xxAsJson() throws Exception {
    mvc.perform(post("/payment")
            .contentType(MediaType.TEXT_PLAIN)
            .content("card_number=2222405343248877"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Unsupported Media Type"));

    mvc.perform(delete("/payment/" + UUID.randomUUID()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpect(jsonPath("$.message").value("Method Not Allowed"));

    Payment payment = new Payment(
        UUID.randomUUID(), PaymentStatus.DECLINED, "0042", 12, 2028, "GBP", 10);
    paymentsRepository.save(payment);

    mvc.perform(get("/payment/" + payment.id()).accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Not Acceptable"));
  }

  @Test
  void unexpectedInternalErrorReturns500WithoutLeakingExceptionMessage() throws Exception {
    UUID failingId = UUID.randomUUID();
    doThrow(new RuntimeException("internal-db-secret")).when(paymentsRepository).findById(failingId);

    mvc.perform(get("/payment/" + failingId))
        .andExpect(status().isInternalServerError())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
  }

  @Test
  void openApiDocsAndSwaggerUiAreAvailable() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/payment'].post.requestBody.content['application/json']")
            .exists())
        .andExpect(jsonPath("$.paths['/payment/{id}'].get").exists());
  }

  @Test
  void emptyJsonObjectAndEmptyBodyReturn400RejectedWithoutCallingBank() throws Exception {
    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Payment request validation failed"));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(""))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists(RequestLoggingFilter.CORRELATION_ID_HEADER))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field type"));

    bankServer.verify();
    verify(paymentsRepository, never()).save(any());
  }

  @Test
  void unknownPropertiesAreIgnoredTwoDigitMonthFormattedAnd0000LastFourPreserved()
      throws Exception {
    bankServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.jsonPath("$.expiry_date").value("12/2028"))
        .andRespond(withSuccess(
            "{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    String requestJson = """
        {
          "card_number": "2222405343240000",
          "expiry_month": 12,
          "expiry_year": 2028,
          "currency": "GBP",
          "amount": 1050,
          "cvv": "123",
          "unknown_extra_field": "ignored"
        }
        """;

    MvcResult postResult = mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0000"))
        .andReturn();

    String location = postResult.getResponse().getHeader("Location");
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0000"));

    bankServer.verify();
  }
}
