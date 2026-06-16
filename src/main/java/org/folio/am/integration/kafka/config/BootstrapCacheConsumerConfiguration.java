package org.folio.am.integration.kafka.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.am.config.properties.BootstrapCacheProperties;
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
import org.springframework.kafka.listener.ContainerProperties.AckMode;
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
  private final BootstrapCacheProperties bootstrapCacheProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>
    bootstrapCacheKafkaListenerContainerFactory(ConsumerFactory<String, DiscoveryEvent> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler());
    // Ephemeral broadcast consumer: never commit offsets (the listener never acks), so per-instance
    // groups leave no lingering offset metadata in __consumer_offsets. Restarts resume from latest.
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);
    return factory;
  }

  @Bean
  public ConsumerFactory<String, DiscoveryEvent> bootstrapCacheConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(DiscoveryEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    // Broadcast: a unique group per instance so EVERY replica receives EVERY event. The prefix is
    // operator-overridable (KAFKA_BOOTSTRAP_CACHE_GROUP_ID) and environment-namespaced by default.
    config.put(GROUP_ID_CONFIG, bootstrapCacheProperties.getGroupIdPrefix() + "-" + UUID.randomUUID());
    // A fresh replica starts cold, so consume only events from start-up onward. A discovery change in
    // the brief window between start-up and first partition assignment may be missed on this replica;
    // the writing replica still evicts in-process, and the TTL backstop bounds any residual staleness.
    config.put(AUTO_OFFSET_RESET_CONFIG, "latest");
    // Never persist offsets for these throwaway groups (see container ack-mode above).
    config.put(ENABLE_AUTO_COMMIT_CONFIG, false);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler errorHandler() {
    var handler = new DefaultErrorHandler((consumerRecord, ex) ->
      log.warn("Failed to process discovery event [record: {}]", consumerRecord, ex.getCause()));
    // best-effort: eviction is idempotent, so no retry/backoff
    handler.setBackOffFunction((consumerRecord, ex) -> new FixedBackOff(0L, 0L));
    handler.setLogLevel(KafkaException.Level.INFO);
    return handler;
  }
}
