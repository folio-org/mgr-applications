package org.folio.am.descriptor;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.test.TestUtils.OBJECT_MAPPER;
import static org.folio.test.TestUtils.readString;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.Permission;
import org.folio.common.domain.model.ProducesEvent;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;

/**
 * Verifies that the internal module descriptor is structurally valid and ready to be bundled into the
 * {@code app-mgr-services} platform application (MGRAPPS-130).
 *
 * <p>The descriptor is loaded from the same classpath location and into the same model that
 * {@code org.folio.security.service.InternalModuleDescriptorProvider} uses at startup, so a failure here is a real
 * startup failure.</p>
 */
@UnitTest
class ModuleDescriptorTest {

  private static final String DESCRIPTOR_PATH = "descriptors/ModuleDescriptor.json";

  private static JsonNode descriptorJson;
  private static ModuleDescriptor descriptor;

  @BeforeAll
  static void beforeAll() {
    var json = readString(DESCRIPTOR_PATH);
    descriptorJson = OBJECT_MAPPER.readTree(json);
    descriptor = OBJECT_MAPPER.readValue(json, ModuleDescriptor.class);
  }

  @Test
  void moduleDescriptor_positive_parsesIntoSharedModel() {
    assertThat(descriptor.getId()).isNotBlank();
    assertThat(descriptor.getDescription()).isNotBlank();
    assertThat(descriptor.getName()).isEqualTo("mgr-applications");
    assertThat(descriptor.getVersion()).isNotBlank();
    assertThat(descriptor.isMgrComponent()).isTrue();
    assertThat(descriptor.getProvides()).isNotEmpty();
    assertThat(descriptor.getPermissionSets()).isNotEmpty();
  }

  @Test
  void moduleDescriptor_positive_handlersAreFullyDefined() {
    assertThat(handlers()).isNotEmpty().allSatisfy(handler -> {
      assertThat(handler.getMethods()).isNotEmpty();
      assertThat(handler.getPathPattern()).isNotBlank();
      assertThat(handler.getPermissionsRequired()).isNotEmpty();
    });

    assertThat(descriptor.getProvides()).allSatisfy(provided -> {
      assertThat(provided.getId()).isNotBlank();
      assertThat(provided.getVersion()).isNotBlank();
    });
  }

  @Test
  void moduleDescriptor_positive_permissionNamesUnique() {
    var permissionNames = descriptor.getPermissionSets().stream()
      .map(Permission::getPermissionName)
      .toList();

    assertThat(permissionNames).isNotEmpty().doesNotHaveDuplicates().allSatisfy(name ->
      assertThat(name).isNotBlank());
  }

  /**
   * mgr-applications publishes the discovery event via {@code DiscoveryPublisher}. The descriptor must declare it
   * under {@code producesEvents} under the same name, so that event dependency checking can validate it once the
   * descriptor is bundled into {@code app-mgr-services} (MGRAPPS-130).
   */
  @Test
  void moduleDescriptor_positive_discoveryEventDeclared() {
    var producedEvents = descriptor.getProducesEvents().stream()
      .map(ProducesEvent::getEvent)
      .toList();

    assertThat(producedEvents).containsExactly(DISCOVERY_DESTINATION);
  }

  /**
   * mgr-applications no longer consumes the entitlement event: Kong route management and its entitlement listener
   * were removed in MGRAPPS-108. It consumes no Kafka events, so the descriptor declares no {@code consumesEvents}
   * (MGRAPPS-130).
   *
   * <p>Asserted on the raw JSON tree rather than on {@link ModuleDescriptor}, because an absent key is
   * indistinguishable from an empty list once deserialized into the model.</p>
   */
  @Test
  void moduleDescriptor_negative_noConsumedEventsDeclared() {
    assertThat(descriptorJson.has("consumesEvents"))
      .as("mgr-applications consumes no Kafka events; see MGRAPPS-108 and MGRAPPS-130 before declaring "
        + "consumesEvents")
      .isFalse();
  }

  /**
   * The declared event name must match the Kafka topic the service actually publishes to, i.e. the {@code name} of
   * one of the topics configured under {@code application.kafka.producer.topics} (the environment prefix is added
   * at runtime by {@code KafkaUtils.getEnvTopicName}, so comparing the unprefixed names is sufficient).
   */
  @Test
  void moduleDescriptor_positive_declaredEventMatchesConfiguredTopic() {
    var declaredEvents = descriptor.getProducesEvents().stream()
      .map(ProducesEvent::getEvent)
      .collect(toSet());

    assertThat(configuredTopicNames()).isEqualTo(declaredEvents);
  }

  private static List<RoutingEntry> handlers() {
    return descriptor.getProvides().stream()
      .map(InterfaceDescriptor::getHandlers)
      .flatMap(List::stream)
      .toList();
  }

  @SneakyThrows
  private static Set<String> configuredTopicNames() {
    var resource = new ClassPathResource("application.yml");
    var propertySource = (EnumerablePropertySource<?>) new YamlPropertySourceLoader()
      .load("application", resource)
      .get(0);

    return Arrays.stream(propertySource.getPropertyNames())
      .filter(name -> name.startsWith("application.kafka.producer.topics[") && name.endsWith("].name"))
      .map(name -> (String) propertySource.getProperty(name))
      .collect(toSet());
  }
}
