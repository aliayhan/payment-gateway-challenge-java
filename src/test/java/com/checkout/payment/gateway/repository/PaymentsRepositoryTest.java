package com.checkout.payment.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.enums.PaymentStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PaymentsRepositoryTest {

  private PaymentsRepository repository;

  @BeforeEach
  void setUp() {
    repository = new PaymentsRepository();
  }

  @Test
  void saveAndFindByIdReturnsStoredPayment() {
    UUID id = UUID.randomUUID();
    Payment payment = new Payment(id, PaymentStatus.AUTHORIZED, "0042", 12, 2028, "GBP", 1050);

    repository.save(payment);

    Optional<Payment> found = repository.findById(id);
    assertThat(found).isNotNull().contains(payment);
  }

  @Test
  void findByIdReturnsEmptyOptionalWhenPaymentDoesNotExist() {
    Optional<Payment> found = repository.findById(UUID.randomUUID());

    assertThat(found).isNotNull().isEmpty();
  }

  @Test
  void saveOverwritesExistingPaymentForSameId() {
    UUID id = UUID.randomUUID();
    Payment initial = new Payment(id, PaymentStatus.AUTHORIZED, "0042", 12, 2028, "GBP", 1050);
    Payment updated = new Payment(id, PaymentStatus.DECLINED, "0042", 12, 2028, "GBP", 2000);

    repository.save(initial);
    repository.save(updated);

    assertThat(repository.findById(id)).isNotNull().contains(updated);
  }

  /**
   * With a plain {@link java.util.HashMap}, concurrent puts/resizes silently drop entries, causing
   * {@code findById} to return empty and fail with {@link AssertionError} inside
   * {@link java.util.concurrent.ExecutionException}. With {@link java.util.concurrent.ConcurrentHashMap},
   * all 5,000 concurrent saves remain immediately visible and retrievable with no lost updates.
   */
  @Test
  void concurrentSavesAndLookupsAcrossTenThreadsPreserveAllPayments() throws Exception {
    int threads = 10;
    int paymentsPerThread = 500;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch readyLatch = new CountDownLatch(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<List<Payment>> perThreadPayments = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      List<Payment> batch = new ArrayList<>(paymentsPerThread);
      for (int j = 0; j < paymentsPerThread; j++) {
        int seq = t * paymentsPerThread + j;
        batch.add(new Payment(
            UUID.randomUUID(),
            seq % 2 == 0 ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
            String.format("%04d", seq % 10_000),
            (seq % 12) + 1,
            2028,
            "GBP",
            100 + seq));
      }
      perThreadPayments.add(batch);
    }

    try {
      List<Future<?>> futures = new ArrayList<>();
      for (List<Payment> batch : perThreadPayments) {
        futures.add(executor.submit(() -> {
          readyLatch.countDown();
          startLatch.await();
          for (Payment payment : batch) {
            repository.save(payment);
            assertThat(repository.findById(payment.id())).contains(payment);
          }
          return null;
        }));
      }

      assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
      startLatch.countDown();

      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
      for (List<Payment> batch : perThreadPayments) {
        for (Payment payment : batch) {
          assertThat(repository.findById(payment.id())).contains(payment);
        }
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
