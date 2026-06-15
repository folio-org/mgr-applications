package org.folio.am.integration.kafka.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheConsumerConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>
    bootstrapCacheKafkaListenerContainerFactory(ConsumerFactory<String, DiscoveryEvent> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler());
    return factory;
  }

  @Bean
  public ConsumerFactory<String, DiscoveryEvent> bootstrapCacheConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(DiscoveryEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    // Broadcast: a unique group per instance so EVERY replica receives EVERY event.
    config.put(GROUP_ID_CONFIG, "mgr-applications-bootstrap-cache-" + UUID.randomUUID());
    // A fresh replica starts cold, so consume only events from start-up onward.
    config.put(AUTO_OFFSET_RESET_CONFIG, "latest");
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler errorHandler() {
    var handler = new DefaultErrorHandler((record, ex) ->
      log.warn("Failed to process discovery event [record: {}]", record, ex.getCause()));
    // best-effort: evict-all is idempotent, so no retry/backoff
    handler.setBackOffFunction((record, ex) -> new FixedBackOff(0L, 0L));
    handler.setLogLevel(KafkaException.Level.INFO);
    return handler;
  }
}
