package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.checkout.payment.gateway.model.exception.PaymentValidationException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates merchant payment requests against gateway rules. */
@Component
public class PaymentRequestValidator {

  private static final int MIN_CARD_LENGTH = 14;
  private static final int MAX_CARD_LENGTH = 19;
  private static final int MIN_MONTH = 1;
  private static final int MAX_MONTH = 12;
  private static final int CURRENCY_LENGTH = 3;
  private static final int MIN_CVV_LENGTH = 3;
  private static final int MAX_CVV_LENGTH = 4;
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("GBP", "USD", "EUR");

  /** Validates the request and throws {@link PaymentValidationException} if any rule fails. */
  public void validateOrThrow(PostPaymentRequest request) {
    List<PaymentRequestError> errors = validate(request);
    if (!errors.isEmpty()) {
      throw new PaymentValidationException(errors);
    }
  }

  /** Validates all request fields and returns safe {@link PaymentRequestError} entries. */
  public List<PaymentRequestError> validate(PostPaymentRequest request) {
    if (request == null) {
      return List.of(PaymentRequestError.REQUEST_REQUIRED);
    }

    List<PaymentRequestError> errors = new ArrayList<>();
    validateCardNumber(request.cardNumber(), errors);
    validateExpiry(request.expiryMonth(), request.expiryYear(), errors);
    validateCurrency(request.currency(), errors);
    validateAmount(request.amount(), errors);
    validateCvv(request.cvv(), errors);
    return List.copyOf(errors);
  }

  private void validateCardNumber(String cardNumber, List<PaymentRequestError> errors) {
    if (cardNumber == null || cardNumber.isBlank()) {
      errors.add(PaymentRequestError.CARD_NUMBER_REQUIRED);
      return;
    }
    if (cardNumber.length() < MIN_CARD_LENGTH || cardNumber.length() > MAX_CARD_LENGTH) {
      errors.add(PaymentRequestError.CARD_NUMBER_LENGTH);
    }
    if (!isAsciiDigits(cardNumber)) {
      errors.add(PaymentRequestError.CARD_NUMBER_NUMERIC);
    }
  }

  private void validateExpiry(
      Integer expiryMonth,
      Integer expiryYear,
      List<PaymentRequestError> errors) {
    boolean monthValid = false;
    if (expiryMonth == null) {
      errors.add(PaymentRequestError.EXPIRY_MONTH_REQUIRED);
    } else if (expiryMonth < MIN_MONTH || expiryMonth > MAX_MONTH) {
      errors.add(PaymentRequestError.EXPIRY_MONTH_RANGE);
    } else {
      monthValid = true;
    }

    if (expiryYear == null) {
      errors.add(PaymentRequestError.EXPIRY_YEAR_REQUIRED);
      return;
    }

    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    if (expiryYear < currentMonth.getYear()) {
      errors.add(PaymentRequestError.EXPIRY_YEAR_EXPIRED);
      return;
    }

    if (monthValid
        && expiryYear == currentMonth.getYear()
        && expiryMonth < currentMonth.getMonthValue()) {
      errors.add(PaymentRequestError.EXPIRY_DATE_EXPIRED);
    }
  }

  private void validateCurrency(String currency, List<PaymentRequestError> errors) {
    if (currency == null || currency.isBlank()) {
      errors.add(PaymentRequestError.CURRENCY_REQUIRED);
      return;
    }
    if (currency.length() != CURRENCY_LENGTH) {
      errors.add(PaymentRequestError.CURRENCY_LENGTH);
      return;
    }
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add(PaymentRequestError.CURRENCY_UNSUPPORTED);
    }
  }

  private void validateAmount(Integer amount, List<PaymentRequestError> errors) {
    if (amount == null) {
      errors.add(PaymentRequestError.AMOUNT_REQUIRED);
      return;
    }
    if (amount <= 0) {
      errors.add(PaymentRequestError.AMOUNT_NOT_POSITIVE);
    }
  }

  private void validateCvv(String cvv, List<PaymentRequestError> errors) {
    if (cvv == null || cvv.isBlank()) {
      errors.add(PaymentRequestError.CVV_REQUIRED);
      return;
    }
    if (cvv.length() < MIN_CVV_LENGTH || cvv.length() > MAX_CVV_LENGTH) {
      errors.add(PaymentRequestError.CVV_LENGTH);
    }
    if (!isAsciiDigits(cvv)) {
      errors.add(PaymentRequestError.CVV_NUMERIC);
    }
  }

  private static boolean isAsciiDigits(String value) {
    return value.chars().allMatch(ch -> ch >= '0' && ch <= '9');
  }
}
