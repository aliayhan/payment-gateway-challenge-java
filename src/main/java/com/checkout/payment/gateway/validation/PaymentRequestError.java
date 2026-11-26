package com.checkout.payment.gateway.validation;

/** Catalog of payment validation errors with stable identifiers independent of enum ordering. */
public enum PaymentRequestError {
  REQUEST_REQUIRED("PR_ERR_REQUEST_REQUIRED", "Payment request is required"),
  CARD_NUMBER_REQUIRED("PR_ERR_CARD_NUMBER_REQUIRED", "Card number is required"),
  CARD_NUMBER_LENGTH("PR_ERR_CARD_NUMBER_LENGTH",
      "Card number must be between 14 and 19 characters"),
  CARD_NUMBER_NUMERIC("PR_ERR_CARD_NUMBER_NUMERIC",
      "Card number must contain only numeric characters"),
  EXPIRY_MONTH_REQUIRED("PR_ERR_EXPIRY_MONTH_REQUIRED", "Expiry month is required"),
  EXPIRY_MONTH_RANGE("PR_ERR_EXPIRY_MONTH_RANGE", "Expiry month must be between 1 and 12"),
  EXPIRY_YEAR_REQUIRED("PR_ERR_EXPIRY_YEAR_REQUIRED", "Expiry year is required"),
  EXPIRY_YEAR_EXPIRED("PR_ERR_EXPIRY_YEAR_EXPIRED", "Expiry year must not be in the past"),
  EXPIRY_DATE_EXPIRED("PR_ERR_EXPIRY_DATE_EXPIRED",
      "Expiry month and year must not be in the past"),
  CURRENCY_REQUIRED("PR_ERR_CURRENCY_REQUIRED", "Currency is required"),
  CURRENCY_LENGTH("PR_ERR_CURRENCY_LENGTH", "Currency must be 3 characters"),
  CURRENCY_UNSUPPORTED("PR_ERR_CURRENCY_UNSUPPORTED", "Currency must be one of GBP, USD, EUR"),
  AMOUNT_REQUIRED("PR_ERR_AMOUNT_REQUIRED", "Amount is required"),
  AMOUNT_NOT_POSITIVE("PR_ERR_AMOUNT_NOT_POSITIVE", "Amount must be greater than 0"),
  CVV_REQUIRED("PR_ERR_CVV_REQUIRED", "CVV is required"),
  CVV_LENGTH("PR_ERR_CVV_LENGTH", "CVV must be 3 or 4 characters"),
  CVV_NUMERIC("PR_ERR_CVV_NUMERIC", "CVV must contain only numeric characters");

  private final String errorId;
  private final String message;

  PaymentRequestError(String errorId, String message) {
    this.errorId = errorId;
    this.message = message;
  }

  public String getErrorId() {
    return errorId;
  }

  public String getMessage() {
    return message;
  }
}
