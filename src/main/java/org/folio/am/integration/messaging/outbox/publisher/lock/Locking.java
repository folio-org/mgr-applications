package org.folio.am.integration.messaging.outbox.publisher.lock;

import lombok.Data;

@Data
public class Locking {

  private static final long DEFAULT_TIMEOUT = 500;
  private static final long DEFAULT_RETRY_DELAY = 150;

  private long timeout = DEFAULT_TIMEOUT;
  private long retryDelay = DEFAULT_RETRY_DELAY;
}
