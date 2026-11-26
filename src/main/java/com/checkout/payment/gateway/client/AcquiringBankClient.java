package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.client.api.BankPaymentRequest;

/** Gateway port for submitting card authorization requests to the acquiring bank. */
public interface AcquiringBankClient {

  BankPaymentDecision submitPayment(BankPaymentRequest request);
}
