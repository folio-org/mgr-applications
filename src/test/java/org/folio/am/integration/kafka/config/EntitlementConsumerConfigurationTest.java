package org.folio.am.integration.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.exception.TenantRouteUpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementConsumerConfigurationTest {

  @Mock private KafkaProperties kafkaProperties;
  @Mock private TenantRoutesRetryConfiguration retryConfiguration;

  @InjectMocks private EntitlementConsumerConfiguration configuration;

  @BeforeEach
  void setUp() {
    lenient().when(kafkaProperties.buildConsumerProperties(null))
      .thenReturn(defaultKafkaConsumerProperties());
    lenient().when(retryConfiguration.getRetryDelay()).thenReturn(Duration.ofMillis(250));
    lenient().when(retryConfiguration.getRetryAttempts()).thenReturn(5L);
  }

  @Test
  void entitlementKafkaListenerContainerFactory_positive() {
    var factory = configuration.entitlementKafkaListenerContainerFactory();

    assertThat(factory).isNotNull();
    assertThat(factory.getConsumerFactory()).isNotNull();

    var consumerFactory = factory.getConsumerFactory();
    var configMap = consumerFactory.getConfigurationProperties();

    assertThat(configMap)
      .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
      .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class)
      .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
  }

  @Test
  void entitlementKafkaListenerContainerFactory_positive_consumerProperties() {
    var factory = configuration.entitlementKafkaListenerContainerFactory();

    var consumerFactory = factory.getConsumerFactory();
    var configMap = consumerFactory.getConfigurationProperties();

    assertThat(configMap)
      .containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
      .containsKey(ConsumerConfig.GROUP_ID_CONFIG);
  }

  @Test
  void backoffLogic_positive_tenantRouteUpdateException() {
    // Create exception with TenantRouteUpdateException as cause
    var tenantUpdateException = new TenantRouteUpdateException("Route update failed");
    var kafkaException = new Exception(tenantUpdateException);

    var backOff = getBackOffForException(kafkaException);

    assertThat(backOff).isInstanceOf(FixedBackOff.class);
    var fixedBackOff = (FixedBackOff) backOff;
    assertThat(fixedBackOff.getInterval()).isEqualTo(250L);
    assertThat(fixedBackOff.getMaxAttempts()).isEqualTo(5L);
  }

  @Test
  void backoffLogic_positive_otherException() {
    // Create exception without TenantRouteUpdateException
    var genericException = new Exception(new RuntimeException("Generic error"));

    var backOff = getBackOffForException(genericException);

    assertThat(backOff).isInstanceOf(FixedBackOff.class);
    var fixedBackOff = (FixedBackOff) backOff;
    assertThat(fixedBackOff.getInterval()).isZero();
    assertThat(fixedBackOff.getMaxAttempts()).isZero();
  }

  @Test
  void backoffLogic_positive_nestedTenantRouteUpdateException() {
    // Create nested exception with TenantRouteUpdateException
    var tenantUpdateException = new TenantRouteUpdateException("Kong route update failed");
    var wrappedException = new RuntimeException(tenantUpdateException);

    var backOff = getBackOffForException(wrappedException);

    assertThat(backOff).isInstanceOf(FixedBackOff.class);
    var fixedBackOff = (FixedBackOff) backOff;
    assertThat(fixedBackOff.getInterval()).isEqualTo(250L);
    assertThat(fixedBackOff.getMaxAttempts()).isEqualTo(5L);
  }

  private BackOff getBackOffForException(Exception exception) {
    try {
      var method = EntitlementConsumerConfiguration.class.getDeclaredMethod("getBackOff", Exception.class);
      method.setAccessible(true);
      return (org.springframework.util.backoff.BackOff) method.invoke(configuration, exception);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke getBackOff method", e);
    }
  }

  private Map<String, Object> defaultKafkaConsumerProperties() {
    var props = new HashMap<String, Object>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    return props;
  }
}
