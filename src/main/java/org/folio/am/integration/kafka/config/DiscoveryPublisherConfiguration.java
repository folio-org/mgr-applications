package org.folio.am.integration.kafka.config;

import org.folio.am.integration.kafka.DiscoveryPublisher;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.integration.messaging.GenericMessagePublisher;
import org.folio.am.integration.messaging.MessagePublisher;
import org.folio.am.integration.messaging.config.MessagingConfiguration;
import org.folio.am.integration.messaging.outbox.config.TrxOutboxConfiguration;
import org.folio.am.integration.messaging.outbox.config.TrxOutboxPublishingConfiguration;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.folio.integration.kafka.EnableKafka;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.core.GenericMessagingTemplate;

@Configuration
@EnableKafka
@Import({TrxOutboxPublishingConfiguration.class, TrxOutboxConfiguration.class, MessagingConfiguration.class})
@ConditionalOnFarModeDisabled
public class DiscoveryPublisherConfiguration {
  @Bean
  public MessagePublisher<DiscoveryEvent> discoverMessagePublisher(GenericMessagingTemplate messagingTemplate) {
    return new GenericMessagePublisher<>(messagingTemplate);
  }

  @Bean
  public DiscoveryPublisher discoveryPublisher(MessagePublisher<DiscoveryEvent> messagePublisher) {
    return new DiscoveryPublisher(messagePublisher);
  }
}
