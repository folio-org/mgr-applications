package org.folio.am.integration.messaging;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public interface MessagePublisher<T> {

  default void send(String destination, T payload) {
    var msg = MessageBuilder.withPayload(payload).build();
    send(destination, msg);
  }

  void send(String destination, Message<T> message);
}
