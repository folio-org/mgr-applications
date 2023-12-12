package org.folio.am.integration.messaging.outbox.store;

import static org.folio.am.integration.messaging.MessageUtils.messageId;
import static org.folio.common.utils.CollectionUtils.mapItems;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;

@Log4j2
public class BatchTrxOutboxStore extends AbstractTrxOutboxStore {

  protected final List<Message<?>> cachedMessages = new ArrayList<>();

  public BatchTrxOutboxStore(TrxOutboxRepository repository, ObjectMapper mapper,
    ApplicationEventPublisher applicationEventPublisher) {
    super(repository, mapper, applicationEventPublisher);
  }

  @Override
  public void beforeCommit(boolean readOnly) {
    if (readOnly) {
      if (!cachedMessages.isEmpty()) {
        log.warn("Transaction is in read-only state! Cached messages won't be saved to outbox table: messageCount = {}",
          cachedMessages.size());
      }

      return;
    }

    log.info("Saving cached messages: count = {}", cachedMessages.size());

    var entities = mapItems(cachedMessages, this::convert);
    repository.saveAll(entities);
  }

  @Override
  protected void saveInternal(Message<?> message) {
    cachedMessages.add(message);
    log.debug("Message stored in the cache: messageId = {}", () -> messageId(message));
  }
}
