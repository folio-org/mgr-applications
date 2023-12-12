package org.folio.am.integration.messaging.outbox.store;

import org.folio.am.integration.messaging.outbox.TrxOutboxException;
import org.springframework.messaging.Message;

public interface TrxOutboxStore {

  void saveMessage(Message<?> message) throws TrxOutboxException;
}
