package org.folio.am.integration.messaging.outbox.store;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.DESTINATION_HEADER;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.PRIMARY_ID_HEADER;
import static org.folio.am.integration.messaging.MessageUtils.messageId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.GenericMessageHeaderAccessor;
import org.folio.am.integration.messaging.outbox.TrxOutboxException;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxEntity;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.folio.am.integration.messaging.outbox.event.OutboxUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Log4j2
@RequiredArgsConstructor
abstract class AbstractTrxOutboxStore implements TrxOutboxStore, TransactionSynchronization {

  protected final TrxOutboxRepository repository;
  protected final ObjectMapper mapper;
  protected final ApplicationEventPublisher applicationEventPublisher;
  protected int savedMessagesCount;

  @PostConstruct
  public void init() {
    TransactionSynchronizationManager.registerSynchronization(this);
  }

  @Override
  public void saveMessage(Message<?> message) throws TrxOutboxException {
    if (log.isDebugEnabled()) {
      log.debug("Saving message to outbox: {}", message);
    } else {
      log.info("Saving message to outbox: messageId = {}", () -> messageId(message));
    }

    validate(message);

    saveInternal(message);
    savedMessagesCount++;

    log.info("Message saved: messageId = {}. Total saved: {}", () -> messageId(message), () -> savedMessagesCount);
  }

  @Override
  public void afterCommit() {
    if (savedMessagesCount > 0) {
      applicationEventPublisher.publishEvent(new OutboxUpdatedEvent(this, savedMessagesCount));

      log.info("Outbox update event is published: messageCount = {}", savedMessagesCount);
    } else {
      log.debug("No messages saved. Outbox update event is not published");
    }
  }

  protected void validate(Message<?> message) {
    if (message == null) {
      throw new TrxOutboxException("Message is null");
    }

    var primaryId = message.getHeaders().get(PRIMARY_ID_HEADER, UUID.class);
    if (primaryId == null) {
      throw new TrxOutboxException(message, "Message id cannot be null");
    }
    var destination = message.getHeaders().get(DESTINATION_HEADER, String.class);
    if (isBlank(destination)) {
      throw new TrxOutboxException(message, "Message destination cannot be null");
    }
  }

  protected abstract void saveInternal(Message<?> message);

  protected TrxOutboxEntity convert(Message<?> message) {
    var entity = new TrxOutboxEntity();

    var accessor = GenericMessageHeaderAccessor.getOrNewAccessor(message);
    entity.setMessageId(accessor.getPrimaryId());
    entity.setDestination(accessor.getDestination());
    entity.setPayload(serialize(message.getPayload()));
    entity.setCreated(millisToOffsetDate(accessor.getCreated()));

    return entity;
  }

  private OffsetDateTime millisToOffsetDate(Long epoch) {
    return epoch != null ? Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC) : null;
  }

  private String serialize(Object payload) {
    // for simplicity payload is expected to be either String or an object that can be serialized to json string
    if (payload instanceof String str) {
      return str;
    } else {
      return toJson(payload);
    }
  }

  private String toJson(Object o) {
    String jsonString;
    try {
      jsonString = mapper.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new TrxOutboxException("Failed to serialize object to a json string: " + e.getMessage());
    }
    return jsonString;
  }
}
