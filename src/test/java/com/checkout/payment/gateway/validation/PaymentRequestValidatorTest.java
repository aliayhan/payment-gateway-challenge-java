package com.checkout.payment.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.checkout.payment.gateway.model.exception.PaymentValidationException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@Tag("unit")
class PaymentRequestValidatorTest {

  private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);
  private static final YearMonth DEC_2026 = YearMonth.of(2026, 12);
  private static final YearMonth JAN_2027 = YearMonth.of(2027, 1);

  private PaymentRequestValidator validator;
  private MockedStatic<YearMonth> yearMonthMock;

  @BeforeEach
  void setUp() {
    validator = new PaymentRequestValidator();
    yearMonthMock = Mockito.mockStatic(YearMonth.class, Mockito.CALLS_REAL_METHODS);
    yearMonthMock.when(() -> YearMonth.now(ZoneOffset.UTC)).thenReturn(NOV_2026);
  }

  @AfterEach
  void tearDown() {
    yearMonthMock.close();
  }

  @Test
  void validMinimumBoundaryRequestPasses() {
    PostPaymentRequest request = new PostPaymentRequest(
        "12345678901234", 1, 2027, "GBP", 1, "123");

    List<PaymentRequestError> errors = validator.validate(request);
    assertThat(errors).isNotNull().isEmpty();
    validator.validateOrThrow(request);
  }

  @Test
  void validMaximumBoundaryRequestPasses() {
    PostPaymentRequest request = new PostPaymentRequest(
        "1234567890123456789", 12, 2026, "EUR", 999999, "1234");

    List<PaymentRequestError> errors = validator.validate(request);
    assertThat(errors).isNotNull().isEmpty();
    validator.validateOrThrow(request);
  }

  @Test
  void currentMonthExpiryIsAccepted() {
    PostPaymentRequest request = validRequest(11, 2026);

    List<PaymentRequestError> errors = validator.validate(request);
    assertThat(errors).isNotNull().isEmpty();
    validator.validateOrThrow(request);
  }

  @Test
  void decemberExpiryAndYearRolloverBoundary() {
    yearMonthMock.when(() -> YearMonth.now(ZoneOffset.UTC)).thenReturn(DEC_2026);
    PostPaymentRequest dec2026 = validRequest(12, 2026);
    assertThat(validator.validate(dec2026)).isNotNull().isEmpty();

    yearMonthMock.when(() -> YearMonth.now(ZoneOffset.UTC)).thenReturn(JAN_2027);
    assertThat(validator.validate(dec2026))
        .isNotNull()
        .containsExactly(PaymentRequestError.EXPIRY_YEAR_EXPIRED);
    assertThat(validator.validate(validRequest(1, 2027))).isNotNull().isEmpty();
  }

  @Test
  void nullRequestIsRejected() {
    assertThat(validator.validate(null))
        .isNotNull()
        .containsExactly(PaymentRequestError.REQUEST_REQUIRED);
    assertThatThrownBy(() -> validator.validateOrThrow(null))
        .isInstanceOf(PaymentValidationException.class);
  }

  @Test
  void allMissingFieldsAreReported() {
    PostPaymentRequest request = new PostPaymentRequest(null, null, null, null, null, null);

    List<PaymentRequestError> errors = validator.validate(request);
    assertThat(errors).isNotNull().containsExactlyInAnyOrder(
        PaymentRequestError.CARD_NUMBER_REQUIRED,
        PaymentRequestError.EXPIRY_MONTH_REQUIRED,
        PaymentRequestError.EXPIRY_YEAR_REQUIRED,
        PaymentRequestError.CURRENCY_REQUIRED,
        PaymentRequestError.AMOUNT_REQUIRED,
        PaymentRequestError.CVV_REQUIRED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567890123", "12345678901234567890"})
  void cardNumberOutside14To19LengthIsRejected(String cardNumber) {
    PostPaymentRequest request = new PostPaymentRequest(cardNumber, 12, 2026, "GBP", 100, "123");

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CARD_NUMBER_LENGTH);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "222240534324887a",
      "2222-4053-4324-88",
      "2222 4053 4324 88",
      "٢٢٢٢٤٠٥٣٤٣٢٤٨٨٧٧"})
  void nonNumericCardNumberIsRejectedWithoutLeakingValue(String cardNumber) {
    PostPaymentRequest request = new PostPaymentRequest(cardNumber, 12, 2026, "GBP", 100, "123");

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CARD_NUMBER_NUMERIC);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 13})
  void expiryMonthOutOfRangeIsRejected(int month) {
    PostPaymentRequest request = validRequest(month, 2027);

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.EXPIRY_MONTH_RANGE);
  }

  @Test
  void pastMonthInCurrentYearIsRejected() {
    assertThat(validator.validate(validRequest(10, 2026)))
        .isNotNull()
        .containsExactly(PaymentRequestError.EXPIRY_DATE_EXPIRED);
  }

  @Test
  void pastYearIsRejectedEvenIfMonthIsGreater() {
    assertThat(validator.validate(validRequest(12, 2025)))
        .isNotNull()
        .containsExactly(PaymentRequestError.EXPIRY_YEAR_EXPIRED);
  }

  @Test
  void anyFutureExpiryYearIsAccepted() {
    assertThat(validator.validate(validRequest(1, 2027))).isNotNull().isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "USD", "EUR"})
  void supportedCurrenciesAreAccepted(String currency) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, currency, 100, "123");

    assertThat(validator.validate(request)).isNotNull().isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GB", "GBPP"})
  void currencyWrongLengthIsRejected(String currency) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, currency, 100, "123");

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CURRENCY_LENGTH);
  }

  @ParameterizedTest
  @ValueSource(strings = {"JPY", "gbp", "CAD"})
  void unsupportedCurrencyIsRejected(String currency) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, currency, 100, "123");

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CURRENCY_UNSUPPORTED);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -500})
  void nonPositiveAmountIsRejected(int amount) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, "GBP", amount, "123");

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.AMOUNT_NOT_POSITIVE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"12", "12345"})
  void cvvWrongLengthIsRejected(String cvv) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, "GBP", 100, cvv);

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CVV_LENGTH);
  }

  @ParameterizedTest
  @ValueSource(strings = {"12a", "1 3", "١٢٣"})
  void nonNumericCvvIsRejected(String cvv) {
    PostPaymentRequest request = new PostPaymentRequest(
        "2222405343248877", 12, 2026, "GBP", 100, cvv);

    assertThat(validator.validate(request))
        .isNotNull()
        .containsExactly(PaymentRequestError.CVV_NUMERIC);
  }

  @Test
  void validateOrThrowThrowsPaymentValidationExceptionWithErrors() {
    PostPaymentRequest invalid = new PostPaymentRequest("123", 0, 2020, "XXX", 0, "ab");

    assertThatThrownBy(() -> validator.validateOrThrow(invalid))
        .isInstanceOf(PaymentValidationException.class)
        .satisfies(ex -> {
          PaymentValidationException pve = (PaymentValidationException) ex;
          assertThat(pve.getErrors()).containsExactlyInAnyOrder(
              PaymentRequestError.CARD_NUMBER_LENGTH,
              PaymentRequestError.EXPIRY_MONTH_RANGE,
              PaymentRequestError.EXPIRY_YEAR_EXPIRED,
              PaymentRequestError.CURRENCY_UNSUPPORTED,
              PaymentRequestError.AMOUNT_NOT_POSITIVE,
              PaymentRequestError.CVV_LENGTH,
              PaymentRequestError.CVV_NUMERIC);
        });
  }

  @Test
  void blankStringsReportRequiredErrorsAndToStringRedactsSensitiveFields() {
    PostPaymentRequest blank = new PostPaymentRequest("   ", 0, 2026, "   ", 100, "   ");

    assertThat(validator.validate(blank)).containsExactlyInAnyOrder(
        PaymentRequestError.CARD_NUMBER_REQUIRED,
        PaymentRequestError.EXPIRY_MONTH_RANGE,
        PaymentRequestError.CURRENCY_REQUIRED,
        PaymentRequestError.CVV_REQUIRED);

    PostPaymentRequest sensitive = validRequest(12, 2026);
    assertThat(sensitive.toString())
        .contains("cardNumber=REDACTED")
        .contains("cvv=REDACTED")
        .doesNotContain("2222405343248877")
        .doesNotContain("123");
  }

  private static PostPaymentRequest validRequest(int month, int year) {
    return new PostPaymentRequest("2222405343248877", month, year, "GBP", 100, "123");
  }
}
