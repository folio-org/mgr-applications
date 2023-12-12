package org.folio.am.integration.messaging.outbox.publisher.lock;

import java.time.OffsetDateTime;
import lombok.Value;

@Value
public class TrxOutboxLock {

  Long lockId;
  String lockedBy;
  OffsetDateTime lockedTime;
}
