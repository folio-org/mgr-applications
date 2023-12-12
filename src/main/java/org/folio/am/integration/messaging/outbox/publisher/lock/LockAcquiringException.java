package org.folio.am.integration.messaging.outbox.publisher.lock;

import org.folio.am.integration.messaging.outbox.TrxOutboxException;

public class LockAcquiringException extends TrxOutboxException {

  public LockAcquiringException(String description, Throwable cause) {
    super(description, cause);
  }
}
