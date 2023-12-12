package org.folio.am.integration.messaging.outbox.publisher.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TrxOutboxLockManagerConstructionTest {

  @Mock private TransactionTemplate transactionTemplate;
  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void construct_positive() {
    Locking locking = locking(100, 30);
    var manager = new TrxOutboxLockManager(locking, transactionTemplate, jdbcTemplate);
    assertThat(manager).isNotNull();
  }

  @Test
  void construct_positive_negativeTimeout() {
    Locking locking = locking(-100, 30);
    var manager = new TrxOutboxLockManager(locking, transactionTemplate, jdbcTemplate);
    assertThat(manager).isNotNull();
  }

  @Test
  void construct_negative_retryDelayNegative() {
    Locking locking = locking(100, -30);

    assertThatThrownBy(() -> new TrxOutboxLockManager(locking, transactionTemplate, jdbcTemplate))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Retry delay cannot be negative");
  }

  @Test
  void construct_negative_retryDelayGreaterTimeout() {
    Locking locking = locking(100, 130);

    assertThatThrownBy(() -> new TrxOutboxLockManager(locking, transactionTemplate, jdbcTemplate))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Retry delay cannot be greater than timeout");
  }

  private static Locking locking(long timeout, long delay) {
    Locking result = new Locking();
    result.setTimeout(timeout);
    result.setRetryDelay(delay);
    return result;
  }
}
