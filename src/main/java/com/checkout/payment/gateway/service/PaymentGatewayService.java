package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.client.BankPaymentDecision;
import com.checkout.payment.gateway.client.api.BankPaymentRequest;
import com.checkout.payment.gateway.controller.api.PostPaymentRequest;
import com.checkout.payment.gateway.model.enums.PaymentStatus;
import com.checkout.payment.gateway.model.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates payment validation, acquiring bank submission, masking, and storage. */
@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final int LAST_FOUR_LENGTH = 4;

  private final PaymentRequestValidator validator;
  private final AcquiringBankClient acquiringBankClient;
  private final PaymentsRepository paymentsRepository;

  public PaymentGatewayService(
      PaymentRequestValidator validator,
      AcquiringBankClient acquiringBankClient,
      PaymentsRepository paymentsRepository) {
    this.validator = validator;
    this.acquiringBankClient = acquiringBankClient;
    this.paymentsRepository = paymentsRepository;
  }

  /**
   * Validates the merchant request, forwards valid requests to the acquiring bank, and stores the
   * resulting masked payment record.
   */
  public Payment processPayment(PostPaymentRequest request) {
    validator.validateOrThrow(request);

    BankPaymentDecision decision = acquiringBankClient.submitPayment(BankPaymentRequest.from(request));
    PaymentStatus status = mapDecision(decision);

    String cardNumber = request.cardNumber();
    String lastFour = cardNumber.substring(cardNumber.length() - LAST_FOUR_LENGTH);

    Payment payment = new Payment(
        UUID.randomUUID(),
        status,
        lastFour,
        request.expiryMonth(),
        request.expiryYear(),
        request.currency(),
        request.amount());

    paymentsRepository.save(payment);
    LOG.debug("Stored paymentId={} status={}", payment.id(), payment.status().getName());
    return payment;
  }

  /** Retrieves a previously stored masked payment by ID. */
  public Payment getPaymentById(UUID id) {
    LOG.debug("Looking up paymentId={}", id);
    return paymentsRepository.findById(id)
        .orElseThrow(() -> new PaymentNotFoundException(id));
  }

  private static PaymentStatus mapDecision(BankPaymentDecision decision) {
    return switch (decision) {
      case AUTHORIZED -> PaymentStatus.AUTHORIZED;
      case DECLINED -> PaymentStatus.DECLINED;
    };
  }
}
