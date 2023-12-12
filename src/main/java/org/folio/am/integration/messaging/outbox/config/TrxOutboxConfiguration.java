package org.folio.am.integration.messaging.outbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.folio.am.integration.messaging.outbox.store.BatchTrxOutboxStore;
import org.folio.am.integration.messaging.outbox.store.TrxOutboxStore;
import org.folio.am.transaction.context.TransactionScope;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@RequiredArgsConstructor
@Import(TrxOutboxPublishingConfiguration.class)
@EnableConfigurationProperties(TrxOutboxProperties.class)
public class TrxOutboxConfiguration {

  private final ObjectMapper mapper;
  private final TrxOutboxRepository outboxRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Bean
  @TransactionScope
  public TrxOutboxStore trxOutboxStore() {
    return new BatchTrxOutboxStore(outboxRepository, mapper, applicationEventPublisher);
  }
}
