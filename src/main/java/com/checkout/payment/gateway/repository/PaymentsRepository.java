package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Thread-safe in-memory store for immutable masked payment records. */
@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, Payment> payments = new ConcurrentHashMap<>();

  /** Persists a payment record keyed by its ID. */
  public void save(Payment payment) {
    payments.put(payment.id(), payment);
  }

  /** Retrieves a payment record by ID if present. */
  public Optional<Payment> findById(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
