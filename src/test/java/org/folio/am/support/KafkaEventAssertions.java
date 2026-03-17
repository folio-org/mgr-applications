package org.folio.am.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.folio.test.FakeKafkaConsumer.getEvents;
import static org.testcontainers.shaded.org.awaitility.Durations.ONE_MINUTE;
import static org.testcontainers.shaded.org.awaitility.Durations.ONE_SECOND;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.am.integration.kafka.model.DiscoveryEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaEventAssertions {

  private static ConditionFactory await() {
    return Awaitility.await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND);
  }

  public static void assertDiscoveryEvents(String... moduleIds) {
    var topic = getEnvTopicName(DISCOVERY_DESTINATION);
    var expectedEvents = Arrays.stream(moduleIds).map(DiscoveryEvent::new).collect(Collectors.toList());
    await().untilAsserted(() -> {
      var consumerRecords = getEvents(topic, DiscoveryEvent.class);
      var entitlementEvents = mapItems(consumerRecords, ConsumerRecord::value);
      assertThat(entitlementEvents).containsSequence(expectedEvents);
    });
  }

  public static void assertNoDiscoveryEvents() {
    var topic = getEnvTopicName(DISCOVERY_DESTINATION);
    await().untilAsserted(() -> {
      var consumerRecords = getEvents(topic, DiscoveryEvent.class);
      assertThat(consumerRecords).isEmpty();
    });
  }
}
