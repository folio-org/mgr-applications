package org.folio.am.integration.messaging.outbox.event;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

@Value
@EqualsAndHashCode(callSuper = false)
public class OutboxUpdatedEvent extends ApplicationEvent {

  int messageCount;

  public OutboxUpdatedEvent(Object source, int messageCount) {
    super(source);
    this.messageCount = messageCount;
  }
}
