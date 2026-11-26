package com.checkout.payment.gateway.client;

/** Definitive payment decision returned by the acquiring bank. */
public enum BankPaymentDecision {
  AUTHORIZED,
  DECLINED
}
