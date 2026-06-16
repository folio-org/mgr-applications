package org.folio.am.integration.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Guards the broadcast consumer's deserialization path: the production
 * {@link org.folio.am.integration.kafka.config.BootstrapCacheConsumerConfiguration} builds a
 * {@code JacksonJsonDeserializer<DiscoveryEvent>}, which can only instantiate the event if it has a
 * no-arg constructor. Without {@code @NoArgsConstructor} on {@link DiscoveryEvent}, deserialization
 * fails and the Kafka invalidation path silently degrades to the TTL backstop, so this test pins it.
 */
@UnitTest
class DiscoveryEventDeserializationTest {

  @Test
  void deserialize_producerJson_roundTripsToDiscoveryEvent() {
    var json = "{\"moduleId\":\"mod-x-1.0.0\"}";

    try (var deserializer = new JacksonJsonDeserializer<>(DiscoveryEvent.class)) {
      var event = deserializer.deserialize("it.discovery", json.getBytes(UTF_8));

      assertThat(event).isNotNull();
      assertThat(event.getModuleId()).isEqualTo("mod-x-1.0.0");
    }
  }

  @Test
  void serializeThenDeserialize_roundTripsToEqualEvent() {
    var original = new DiscoveryEvent("mod-x-1.0.0");

    try (var serializer = new JacksonJsonSerializer<DiscoveryEvent>();
         var deserializer = new JacksonJsonDeserializer<>(DiscoveryEvent.class)) {
      var bytes = serializer.serialize("it.discovery", original);
      var restored = deserializer.deserialize("it.discovery", bytes);

      assertThat(restored).isEqualTo(original);
    }
  }
}
