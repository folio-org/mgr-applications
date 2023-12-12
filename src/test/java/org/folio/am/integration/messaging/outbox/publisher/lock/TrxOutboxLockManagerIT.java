package org.folio.am.integration.messaging.outbox.publisher.lock;

import static org.apache.commons.lang3.ObjectUtils.identityToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.folio.am.support.base.BaseRepositoryTest;
import org.folio.test.extensions.LogTestMethod;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@IntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@LogTestMethod
class TrxOutboxLockManagerIT extends BaseRepositoryTest {

  public static final String LOCK_STATEMENT = """
    UPDATE trx_outbox_lock SET locked = TRUE, locked_by = 'Loki', locked_time = CURRENT_TIMESTAMP WHERE id = 1
    """;
  public static final String UNLOCK_STATEMENT = """
    UPDATE trx_outbox_lock SET locked = FALSE, locked_by = NULL, locked_time = NULL WHERE id = 1
    """;

  @Autowired
  @Qualifier("testTransactionTemplate")
  private TransactionTemplate transactionTemplate;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    unlock();
    log.info("Unlock statement executed");
  }

  @Test
  void acquire_successful_if_notLockedPreviously() {
    var time = OffsetDateTime.now();
    var manager = new TrxOutboxLockManager(locking(-1, 100), transactionTemplate, jdbcTemplate);

    var lock = manager.acquire(this);

    assertThat(lock).isPresent();
    assertThat(lock.get().getLockId()).isNotNull();
    assertThat(lock.get().getLockedBy()).isEqualTo(identityToString(this));
    assertThat(lock.get().getLockedTime()).isAfterOrEqualTo(time);

    assertThat(tableRowLocked(true)).isTrue();
  }

  @Test
  @Sql(statements = LOCK_STATEMENT)
  void acquire_successful_if_unLockedWithinTimeout() {
    final var time = OffsetDateTime.now();
    var manager = new TrxOutboxLockManager(locking(400, 100), transactionTemplate, jdbcTemplate);

    var executor = Executors.newSingleThreadScheduledExecutor();
    executor.schedule(this::unlock, 150, TimeUnit.MILLISECONDS);

    var lock = manager.acquire(this);

    assertThat(lock).isPresent();
    assertThat(lock.get().getLockId()).isNotNull();
    assertThat(lock.get().getLockedBy()).isEqualTo(identityToString(this));
    assertThat(lock.get().getLockedTime()).isAfterOrEqualTo(time);

    assertThat(tableRowLocked(true)).isTrue();
  }

  @Test
  @Sql(statements = LOCK_STATEMENT)
  void acquire_failed_if_notUnlockedWithinTimeout() {
    var manager = new TrxOutboxLockManager(locking(200, 100), transactionTemplate, jdbcTemplate);

    var lock = manager.acquire(this);

    assertThat(lock).isNotPresent();
  }

  @Test
  @Sql(statements = LOCK_STATEMENT)
  void release_successful() {
    var manager = new TrxOutboxLockManager(new Locking(), transactionTemplate, jdbcTemplate);

    manager.release(new TrxOutboxLock(1L, "Loki", OffsetDateTime.now()));

    assertThat(tableRowLocked(false)).isTrue();
  }

  private void unlock() {
    jdbcTemplate.update(UNLOCK_STATEMENT);
  }

  private boolean tableRowLocked(boolean locked) {
    try {
      return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject("SELECT TRUE FROM trx_outbox_lock WHERE id = 1 AND locked = ?",
          Boolean.class, locked)
      );
    } catch (EmptyResultDataAccessException e) {
      return false;
    }
  }

  private static Locking locking(long timeout, long delay) {
    Locking result = new Locking();
    result.setTimeout(timeout);
    result.setRetryDelay(delay);
    return result;
  }

  @TestConfiguration
  public static class TemplateConfiguration {

    @Bean
    public TransactionTemplate testTransactionTemplate(PlatformTransactionManager transactionManager) {
      var transactionTemplate = new TransactionTemplate(transactionManager);
      transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
      return transactionTemplate;
    }
  }
}
