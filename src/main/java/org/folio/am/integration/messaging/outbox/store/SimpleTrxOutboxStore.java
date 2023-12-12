package org.folio.am.integration.messaging.outbox.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;

@Log4j2
public class SimpleTrxOutboxStore extends AbstractTrxOutboxStore {

  public SimpleTrxOutboxStore(TrxOutboxRepository repository, ObjectMapper mapper,
    ApplicationEventPublisher applicationEventPublisher) {
    super(repository, mapper, applicationEventPublisher);
  }

  @Override
  protected void saveInternal(Message<?> message) {
    var outboxEntity = convert(message);
    log.debug("Message converted to outbox entity: {}", outboxEntity);

    repository.save(outboxEntity);
  }
}
