package org.folio.am.integration.kafka.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.am.integration.kafka.model.TenantEntitlementEvent;
import org.folio.tools.kong.exception.TenantRouteUpdateException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "application.kong.tenant-checks.enabled", havingValue = "true")
public class EntitlementConsumerConfiguration {

  private final KafkaProperties kafkaProperties;
  private final TenantRoutesRetryConfiguration retryConfiguration;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, TenantEntitlementEvent>
    entitlementKafkaListenerContainerFactory(
    ConsumerFactory<String, TenantEntitlementEvent> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, TenantEntitlementEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(eventErrorHandler());
    return factory;
  }

  @Bean
  public ConsumerFactory<String, TenantEntitlementEvent> consumerFactory() {
    var deserializer = new JsonDeserializer<>(TenantEntitlementEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler eventErrorHandler() {
    var errorHandler = new DefaultErrorHandler((message, exception) ->
      log.debug("Failed to process event [record: {}]", message, exception.getCause()));
    errorHandler.setBackOffFunction((message, exception) -> getBackOff(exception));
    errorHandler.setLogLevel(KafkaException.Level.INFO);

    return errorHandler;
  }

  private BackOff getBackOff(Exception exception) {
    if (hasTenantRouteUpdateException(exception)) {
      log.debug("Error during tenant routes change [message: {}]", exception.getMessage());
      return new FixedBackOff(retryConfiguration.getRetryDelay().toMillis(), retryConfiguration.getRetryAttempts());
    }

    return new FixedBackOff(0L, 0L);
  }

  private boolean hasTenantRouteUpdateException(Exception exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof TenantRouteUpdateException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
