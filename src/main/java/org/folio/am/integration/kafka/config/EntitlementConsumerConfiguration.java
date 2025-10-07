package org.folio.am.integration.kafka.config;

import java.util.HashMap;
import java.util.Optional;
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
    entitlementKafkaListenerContainerFactory() {
    var props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    var jd = new JsonDeserializer<>(TenantEntitlementEvent.class);
    jd.addTrustedPackages("org.folio.am.integration.kafka.model");

    var cf = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jd);
    var factory = new ConcurrentKafkaListenerContainerFactory<String, TenantEntitlementEvent>();

    factory.setConsumerFactory(cf);
    factory.setCommonErrorHandler(eventErrorHandler());
    return factory;
  }

  private DefaultErrorHandler eventErrorHandler() {
    var errorHandler = new DefaultErrorHandler((message, exception) ->
      log.warn("Failed to process event [record: {}]", message, exception.getCause()));
    errorHandler.setBackOffFunction((message, exception) -> getBackOff(exception));
    errorHandler.setLogLevel(KafkaException.Level.DEBUG);

    return errorHandler;
  }

  private BackOff getBackOff(Exception exception) {
    var throwable = findTenantUpdateException(exception);
    if (throwable.isPresent()) {
      log.warn("Error during tenant routes change [message: {}]", throwable.get().getMessage());
      return new FixedBackOff(retryConfiguration.getRetryDelay().toMillis(), retryConfiguration.getRetryAttempts());
    }

    return new FixedBackOff(0L, 0L);
  }

  private static Optional<Throwable> findTenantUpdateException(Exception exception) {
    return Optional.of(exception)
      .map(Throwable::getCause)
      .filter(TenantRouteUpdateException.class::isInstance);
  }
}
