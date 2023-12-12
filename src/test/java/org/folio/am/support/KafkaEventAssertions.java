package org.folio.am.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.folio.test.FakeKafkaConsumer.getEvents;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.testcontainers.shaded.org.awaitility.Durations;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaEventAssertions {

  private static ConditionFactory await() {
    return Awaitility.await().atMost(Durations.FIVE_SECONDS);
  }

  public static void assertDiscoveryEvents(String... moduleIds) {
    var topic = getEnvTopicName(DISCOVERY_DESTINATION);
    var kafkaEvents = await().until(() -> getEvents(topic, DiscoveryEvent.class), hasSize(moduleIds.length));
    var expectedEvents = Arrays.stream(moduleIds).map(DiscoveryEvent::new).collect(Collectors.toList());
    assertThat(kafkaEvents).map(ConsumerRecord::value).containsExactlyInAnyOrderElementsOf(expectedEvents);
  }

  public static void assertNoDiscoveryEvents() {
    assertDiscoveryEvents(ArrayUtils.EMPTY_STRING_ARRAY);
  }
}
