package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.controller.api.ErrorResponse;
import com.checkout.payment.gateway.model.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.exception.PaymentValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps gateway domain and transport exceptions to safe, consistent HTTP responses.
 *
 * <p>Error bodies are always written as JSON, independent of the request's {@code Accept}
 * header, so a client that negotiates an unsupported media type still receives a readable error
 * instead of an empty {@code 500}.
 */
@RestControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  // --- Application exceptions (thrown explicitly by gateway validation, service, and bank client) ---

  @ExceptionHandler(PaymentValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(PaymentValidationException ex) {
    // List of Errors can be given back to the user if requirements would ask for it - we have skipped it here for now.
    return error(HttpStatus.BAD_REQUEST,
        ErrorResponse.rejected("Payment request validation failed"));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, new ErrorResponse("ID not found"));
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    return error(HttpStatus.SERVICE_UNAVAILABLE,
        new ErrorResponse("Acquiring bank is currently unavailable"));
  }

  // --- Internal Spring MVC exceptions (thrown by Spring before entering controller methods) ---

  /** Example: {@code POST /payment} with malformed JSON ({@code {bad}) or {@code "amount": "abc"}. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST,
        ErrorResponse.rejected("Malformed JSON request or invalid field type"));
  }

  /** Example: {@code GET /payment/not-a-uuid} where {@code not-a-uuid} cannot bind to {@code UUID}. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return error(HttpStatus.BAD_REQUEST,
        new ErrorResponse("Invalid payment ID format; expected a UUID"));
  }

  /**
   * Handles Spring MVC HTTP method and media-type negotiation errors so the catch-all
   * {@code Exception} handler does not turn them into {@code 500}:
   * <ul>
   *   <li><b>405 Method Not Allowed</b> ({@link HttpRequestMethodNotSupportedException}): e.g.
   *       {@code DELETE /payment/{id}} when only {@code GET} is supported (includes {@code Allow}).
   *   <li><b>415 Unsupported Media Type</b> ({@link HttpMediaTypeNotSupportedException}): e.g.
   *       {@code POST /payment} with {@code Content-Type: text/plain} instead of
   *       {@code application/json}.
   *   <li><b>406 Not Acceptable</b> ({@link HttpMediaTypeNotAcceptableException}): e.g.
   *       {@code GET /payment/{id}} with {@code Accept: application/xml} when only JSON is
   *       produced.
   * </ul>
   */
  @ExceptionHandler({
      HttpRequestMethodNotSupportedException.class,
      HttpMediaTypeNotSupportedException.class,
      HttpMediaTypeNotAcceptableException.class})
  public ResponseEntity<ErrorResponse> handleUnsupportedRequest(Exception ex) {
    // All three exceptions implement Spring's ErrorResponse interface, exposing their 4xx status and headers.
    org.springframework.web.ErrorResponse springError = (org.springframework.web.ErrorResponse) ex;
    HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
    return error(status, springError.getHeaders(), new ErrorResponse(status.getReasonPhrase()));
  }

  // --- Catch-all fallback ---

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    LOG.error("Unhandled gateway exception", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR,
        new ErrorResponse("An unexpected error occurred"));
  }

  private static ResponseEntity<ErrorResponse> error(HttpStatus status, ErrorResponse body) {
    return error(status, HttpHeaders.EMPTY, body);
  }

  /** Presetting the content type makes Spring skip Accept negotiation for error bodies. */
  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      HttpHeaders headers,
      ErrorResponse body) {
    return ResponseEntity.status(status)
        .headers(headers)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
