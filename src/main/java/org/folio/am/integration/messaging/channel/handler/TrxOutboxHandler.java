package org.folio.am.integration.messaging.channel.handler;

import lombok.RequiredArgsConstructor;
import org.folio.am.integration.messaging.outbox.store.TrxOutboxStore;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

@RequiredArgsConstructor
public class TrxOutboxHandler implements MessageHandler {

  private final TrxOutboxStore outboxStore;

  @Override
  public void handleMessage(Message<?> message) throws MessagingException {
    outboxStore.saveMessage(message);
  }
}
