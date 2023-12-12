package org.folio.am.integration.messaging;

import static org.folio.am.integration.messaging.MessageUtils.messageId;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.Message;
import org.springframework.messaging.core.GenericMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

@Log4j2
@RequiredArgsConstructor
public class GenericMessagePublisher<T> implements MessagePublisher<T> {

  private final GenericMessagingTemplate messagingTemplate;

  @Override
  public void send(String destination, Message<T> message) {
    var accessor = GenericMessageHeaderAccessor.wrap(message);
    accessor.setDestination(destination);

    var msg = MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());

    log.info("Sending message: destination = {}, messageId = {}", () -> destination, () -> messageId(msg));
    messagingTemplate.send(destination, msg);
  }
}
