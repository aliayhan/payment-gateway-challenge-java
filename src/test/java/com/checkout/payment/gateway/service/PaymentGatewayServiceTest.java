package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.client.BankPaymentDecision;
import com.checkout.payment.gateway.client.api.BankPaymentRequest;
import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.enums.PaymentStatus;
import com.checkout.payment.gateway.model.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.exception.PaymentValidationException;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@Tag("unit")
class PaymentGatewayServiceTest {

  private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

  private AcquiringBankClient acquiringBankClient;
  private PaymentsRepository paymentsRepository;
  private PaymentGatewayService service;
  private MockedStatic<YearMonth> yearMonthMock;

  @BeforeEach
  void setUp() {
    yearMonthMock = Mockito.mockStatic(YearMonth.class, Mockito.CALLS_REAL_METHODS);
    yearMonthMock.when(() -> YearMonth.now(ZoneOffset.UTC)).thenReturn(NOV_2026);
    acquiringBankClient = mock(AcquiringBankClient.class);
    paymentsRepository = mock(PaymentsRepository.class);
    service = new PaymentGatewayService(
        new PaymentRequestValidator(),
        acquiringBankClient,
        paymentsRepository);
  }

  @AfterEach
  void tearDown() {
    yearMonthMock.close();
  }

  @Test
  void authorizedPaymentCallsBankMasksCardPreservingLeadingZerosAndSaves() {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343240041", 4, 2028, "GBP", 1050, "099");
    when(acquiringBankClient.submitPayment(any())).thenReturn(BankPaymentDecision.AUTHORIZED);

    Payment payment = service.processPayment(request);

    assertThat(payment).isNotNull();
    assertThat(payment.id()).isNotNull();
    assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.cardNumberLastFour()).isEqualTo("0041");
    assertThat(payment.expiryMonth()).isEqualTo(4);
    assertThat(payment.expiryYear()).isEqualTo(2028);
    assertThat(payment.currency()).isEqualTo("GBP");
    assertThat(payment.amount()).isEqualTo(1050);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(acquiringBankClient).submitPayment(captor.capture());
    assertThat(captor.getValue().expiryDate()).isEqualTo("04/2028");
    assertThat(captor.getValue().cvv()).isEqualTo("099");
    verify(paymentsRepository).save(payment);
  }

  @Test
  void declinedPaymentIsSavedAndReturned() {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343240042", 12, 2028, "USD", 2500, "123");
    when(acquiringBankClient.submitPayment(any())).thenReturn(BankPaymentDecision.DECLINED);

    Payment payment = service.processPayment(request);

    assertThat(payment).isNotNull();
    assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.cardNumberLastFour()).isEqualTo("0042");
    verify(paymentsRepository).save(payment);
  }

  @Test
  void invalidRequestDoesNotCallBankOrSavePayment() {
    PostPaymentRequest invalid = new PostPaymentRequest("1234", 12, 2026, "GBP", 100, "123");

    assertThatThrownBy(() -> service.processPayment(invalid))
        .isInstanceOf(PaymentValidationException.class);

    verifyNoInteractions(acquiringBankClient);
    verify(paymentsRepository, never()).save(any());
  }

  @Test
  void bankFailureDoesNotSavePayment() {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248870", 12, 2028, "EUR", 500, "123");
    when(acquiringBankClient.submitPayment(any()))
        .thenThrow(new BankUnavailableException("Bank down"));

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(BankUnavailableException.class);

    verify(paymentsRepository, never()).save(any());
  }

  @Test
  void getPaymentByIdReturnsStoredPayment() {
    UUID id = UUID.randomUUID();
    Payment stored = new Payment(id, PaymentStatus.AUTHORIZED, "0041", 4, 2028, "GBP", 1050);
    when(paymentsRepository.findById(id)).thenReturn(Optional.of(stored));

    Payment found = service.getPaymentById(id);

    assertThat(found).isEqualTo(stored);
  }

  @Test
  void unknownPaymentIdThrowsPaymentNotFoundException() {
    UUID unknownId = UUID.randomUUID();
    when(paymentsRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(unknownId))
        .isInstanceOf(PaymentNotFoundException.class);
  }
}
