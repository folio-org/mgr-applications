package org.folio.am.descriptor;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.integration.kafka.DiscoveryPublisher.PRODUCED_EVENT_DISCOVERY;
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

  @Test
  void moduleDescriptor_positive_discoveryEventDeclared() {
    var producedEvents = descriptor.getProducesEvents().stream()
      .map(ProducesEvent::getEvent)
      .toList();

    assertThat(producedEvents).containsExactly(PRODUCED_EVENT_DISCOVERY);
  }

  @Test
  void moduleDescriptor_positive_discoveryEventIdentifierFollowsNamingConvention() {
    assertThat(PRODUCED_EVENT_DISCOVERY)
      .as("event identifier should be <module>.<resource>.<event-type>.vN, not the bare topic/channel name")
      .matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2,}\\.v\\d+")
      .startsWith(descriptor.getName() + ".")
      .isNotEqualTo(DISCOVERY_DESTINATION);
  }

  @Test
  void moduleDescriptor_negative_noConsumedEventsDeclared() {
    assertThat(descriptorJson.has("consumesEvents"))
      .as("mgr-applications consumes no Kafka events; see MGRAPPS-108 and MGRAPPS-130 before declaring "
        + "consumesEvents")
      .isFalse();
  }

  @Test
  void moduleDescriptor_positive_discoveryTopicStillConfigured() {
    assertThat(configuredTopicNames()).contains(DISCOVERY_DESTINATION);
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
      .getFirst();

    return Arrays.stream(propertySource.getPropertyNames())
      .filter(name -> name.startsWith("application.kafka.producer.topics[") && name.endsWith("].name"))
      .map(name -> (String) propertySource.getProperty(name))
      .collect(toSet());
  }
}
