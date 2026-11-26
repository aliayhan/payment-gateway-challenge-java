package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.controller.api.ErrorResponse;
import com.checkout.payment.gateway.controller.api.PaymentResponse;
import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API controller for submitting and retrieving card payments. */
@RestController
@Tag(name = "Payments", description = "Submit card payments and retrieve masked payment details")
public class PaymentGatewayController {

  private static final String AUTHORIZED_PAYMENT_EXAMPLE = """
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "status": "Authorized",
        "cardNumberLastFour": "8877",
        "expiryMonth": 12,
        "expiryYear": 2028,
        "currency": "GBP",
        "amount": 1050
      }
      """;
  private static final String DECLINED_PAYMENT_EXAMPLE = """
      {
        "id": "660e8400-e29b-41d4-a716-446655440001",
        "status": "Declined",
        "cardNumberLastFour": "0042",
        "expiryMonth": 12,
        "expiryYear": 2028,
        "currency": "USD",
        "amount": 2500
      }
      """;

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(
      summary = "Submit a card payment",
      description = "Validates the card payment request and forwards valid payments to the "
          + "acquiring bank simulator. Stores both Authorized and Declined payments.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Payment processed by the acquiring bank (Authorized or Declined)",
          headers = {
              @Header(
                  name = "Location",
                  description = "Relative URI to retrieve the created payment",
                  schema = @Schema(type = "string", example = "/payment/550e8400-e29b-41d4-a716-446655440000")),
              @Header(
                  name = "X-Request-Id",
                  description = "Correlation identifier for this request",
                  schema = @Schema(type = "string", example = "9c4c4f6b-7b4b-4a53-b0b7-66a371225777"))
          },
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaymentResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Authorized payment",
                      value = AUTHORIZED_PAYMENT_EXAMPLE),
                  @ExampleObject(
                      name = "Declined payment",
                      value = DECLINED_PAYMENT_EXAMPLE)
              })),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid payment request (Rejected without calling bank) or malformed JSON",
          headers = @Header(
              name = "X-Request-Id",
              description = "Correlation identifier for this request",
              schema = @Schema(type = "string")),
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  name = "Rejected payment",
                  value = """
                      {
                        "status": "Rejected",
                        "message": "Payment request validation failed"
                      }
                      """))),
      @ApiResponse(
          responseCode = "503",
          description = "Acquiring bank is unavailable, timed out, or returned an unexpected error",
          headers = @Header(
              name = "X-Request-Id",
              description = "Correlation identifier for this request",
              schema = @Schema(type = "string")),
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  name = "Bank unavailable",
                  value = """
                      {
                        "message": "Acquiring bank is currently unavailable"
                      }
                      """)))
  })
  @PostMapping(path = "/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PaymentResponse> submitPayment(@RequestBody PostPaymentRequest request) {
    Payment payment = paymentGatewayService.processPayment(request);
    URI location = URI.create("/payment/" + payment.id());
    return ResponseEntity.created(location).body(PaymentResponse.from(payment));
  }

  @Operation(
      summary = "Retrieve payment details",
      description = "Retrieves a previously processed Authorized or Declined payment by ID.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Masked payment details found",
          headers = @Header(
              name = "X-Request-Id",
              description = "Correlation identifier for this request",
              schema = @Schema(type = "string")),
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaymentResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Authorized payment",
                      value = AUTHORIZED_PAYMENT_EXAMPLE),
                  @ExampleObject(
                      name = "Declined payment",
                      value = DECLINED_PAYMENT_EXAMPLE)
              })),
      @ApiResponse(
          responseCode = "400",
          description = "Malformed payment ID (expected UUID)",
          headers = @Header(
              name = "X-Request-Id",
              description = "Correlation identifier for this request",
              schema = @Schema(type = "string")),
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  name = "Invalid payment ID",
                  value = """
                      {
                        "message": "Invalid payment ID format; expected a UUID"
                      }
                      """))),
      @ApiResponse(
          responseCode = "404",
          description = "No payment exists with the specified ID",
          headers = @Header(
              name = "X-Request-Id",
              description = "Correlation identifier for this request",
              schema = @Schema(type = "string")),
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  name = "Unknown payment",
                  value = """
                      {
                        "message": "ID not found"
                      }
                      """)))
  })
  @GetMapping("/payment/{id}")
  public ResponseEntity<PaymentResponse> getPaymentById(
      @Parameter(
          description = "Gateway payment UUID",
          example = "550e8400-e29b-41d4-a716-446655440000")
      @PathVariable UUID id) {
    Payment payment = paymentGatewayService.getPaymentById(id);
    return ResponseEntity.ok(PaymentResponse.from(payment));
  }
}
