package org.folio.am.integration.messaging.outbox.publisher.lock;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

@Log4j2
public class TrxOutboxLockManager {

  private static final int INFINITE_TIMEOUT = -1;
  private static final long LOCK_REC_ID = 1;

  private static final String LOCK_CHECK_QUERY = """
    SELECT 1
      FROM trx_outbox_lock
      WHERE id = ?
        AND locked = ?
      FOR UPDATE NOWAIT
    """;

  private static final String LOCK_UPDATE = """
    UPDATE trx_outbox_lock
      SET locked = ?
        , locked_by = ?
        , locked_time = ?
      WHERE id = ?
    """;

  private final TransactionTemplate transactionTemplate;
  private final JdbcTemplate jdbcTemplate;
  private final long timeout;
  private final long retryDelay;

  public TrxOutboxLockManager(Locking locking, TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate) {
    this.timeout = locking.getTimeout() < 0 ? INFINITE_TIMEOUT : locking.getTimeout();

    if (this.timeout == 0) {
      this.retryDelay = 0;
    } else {
      var delay = locking.getRetryDelay();

      Assert.isTrue(delay >= 0, "Retry delay cannot be negative: provided = " + delay);
      Assert.isTrue(timeout == INFINITE_TIMEOUT || delay <= this.timeout,
        "Retry delay cannot be greater than timeout: retryDelay = " + delay + ", timeout = " + timeout);

      this.retryDelay = delay;
    }

    this.transactionTemplate = transactionTemplate;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<TrxOutboxLock> acquire(Object requester) {
    TrxOutboxLock lock;
    long startTime = System.currentTimeMillis();

    do {
      lock = acquireDbLock(requester);

      if (lock == null) {
        log.debug("Lock cannot be acquired, suspending...");
        suspend();
      }
    } while (lock == null && !timeoutExceeded(startTime));

    return Optional.ofNullable(lock);
  }

  public void release(TrxOutboxLock lock) {
    releaseLock(lock);
  }

  private TrxOutboxLock acquireDbLock(Object requester) {
    return transactionTemplate.execute(status -> canBeLocked() ? saveLock(requester) : null);
  }

  private Boolean canBeLocked() {
    try {
      var result = jdbcTemplate.queryForObject(LOCK_CHECK_QUERY,
        (rs, rowNum) -> rs.getInt(1) == 1, LOCK_REC_ID, false);
      log.trace("Lock acquisition checking query result: {}", result);

      return result;
    } catch (EmptyResultDataAccessException e) {
      log.trace("Lock acquisition checking query result: EMPTY. Cannot be locked");
      return false;
    }
  }

  private void suspend() {
    try {
      if (timeout != 0 && retryDelay != 0) {
        log.trace("Putting the thread to sleep for {}mills", retryDelay);
        Thread.sleep(retryDelay);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LockAcquiringException("Failed to acquire outbox table lock: process interrupted", e);
    }
  }

  private boolean timeoutExceeded(long startTime) {
    if (timeout == INFINITE_TIMEOUT) {
      return false;
    }

    long currentTime = System.currentTimeMillis();
    var result = currentTime >= startTime + timeout;
    log.debug("Lock acquisition timeout: {}", result ? "Exceeded" : "Valid");

    return result;
  }

  private TrxOutboxLock saveLock(Object requester) {
    var lockedBy = ObjectUtils.identityToString(requester);
    var lockedTime = OffsetDateTime.now();

    updateDbEntry(LOCK_REC_ID, true, lockedBy, lockedTime);
    log.trace("Lock saved: lockedBy = {}, lockedTime = {}", lockedBy, lockedTime);

    return new TrxOutboxLock(LOCK_REC_ID, lockedBy, lockedTime);
  }

  private void releaseLock(TrxOutboxLock lock) {
    updateDbEntry(lock.getLockId(), false, null, null);
    log.trace("Lock removed: lockedBy = {}, lockedTime = {}", lock.getLockedBy(), lock.getLockedTime());
  }

  private void updateDbEntry(Long lockId, boolean locked, String lockedBy, OffsetDateTime lockedTime) {
    int updated = jdbcTemplate.update(LOCK_UPDATE, locked, lockedBy, lockedTime, lockId);
    if (updated != 1) {
      throw new IncorrectResultSizeDataAccessException(1, updated);
    }
  }
}
