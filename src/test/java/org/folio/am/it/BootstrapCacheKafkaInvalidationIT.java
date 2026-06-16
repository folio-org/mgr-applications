package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.am.config.cache.BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.am.support.config.KafkaTestConfiguration;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * Proves the Kafka broadcast path ALONE invalidates the per-replica module-bootstrap cache, including
 * the provider fan-out.
 *
 * <p>Unlike {@link BootstrapCacheInvalidationIT} (which changes discovery via REST and therefore also
 * triggers the in-process {@code afterCommit} evictor), this test warms the cache and then publishes a
 * {@link DiscoveryEvent} straight to the discovery topic — no discovery REST call happens between the
 * warm-up and the assertion, so the only thing that can evict is
 * {@link org.folio.am.integration.kafka.BootstrapCacheInvalidationListener}. A successful eviction
 * proves the broadcast consumer received and deserialized the event end-to-end, and (in the fan-out
 * case) that the reverse-dependency lookup is actually consulted on the consumer path.
 *
 * <p>The broadcast consumer uses {@code auto-offset-reset=latest}, so the event is (re)published inside
 * the awaitility loop: until the consumer is assigned and positioned, an event could be produced ahead
 * of its read position and missed. Re-publishing is harmless because eviction is idempotent.
 */
@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@Import({KafkaTestConfiguration.class, BootstrapCacheKafkaInvalidationIT.DiscoveryTopicConfiguration.class})
@TestPropertySource(properties = {
  "application.okapi.enabled=false",
  "application.kong.enabled=false",
  "application.bootstrap-cache.enabled=true"
})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class BootstrapCacheKafkaInvalidationIT extends BaseIntegrationTest {

  @Autowired private KeycloakProperties keycloakProperties;
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private CacheManager cacheManager;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @Value("${spring.kafka.topics.discovery}")
  private String discoveryTopic;

  @Test
  void discoveryEventOnKafka_invalidatesChangedModule() throws Exception {
    var moduleId = "mod-kafka-cache-1.0.0";
    postApplication(new ApplicationDescriptor()
      .name("kafka-cache-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-kafka-cache").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(moduleId)
        .provides(List.of(new InterfaceDescriptor().id("kafka-cache-int").version("1.0").interfaceType("multiple"))))));
    createDiscovery(moduleId, "http://mod-kafka-cache:8081");

    awaitListenerAssignment();
    var cache = cacheManager.getCache(MODULE_BOOTSTRAP_CACHE);
    assertThat(cache).isNotNull();

    // warm immediately before publishing - no discovery REST call in between, so the in-process
    // invalidator cannot be the cause of the eviction asserted below
    getBootstrap(moduleId);
    assertThat(cache.get(moduleId)).as("cache should be warmed by the GET").isNotNull();

    awaitEvictionByKafka(cache, moduleId, moduleId);
  }

  @Test
  void discoveryEventOnKafka_invalidatesDependentModulesViaFanOut() throws Exception {
    var providerId = "mod-fanout-provider-1.0.0";
    var consumerId = "mod-fanout-consumer-1.0.0";
    postApplication(new ApplicationDescriptor()
      .name("fanout-provider-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-fanout-provider").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(providerId)
        .provides(List.of(new InterfaceDescriptor().id("fanout-int").version("1.0").interfaceType("multiple"))))));
    createDiscovery(providerId, "http://mod-fanout-provider:8081");

    postApplication(new ApplicationDescriptor()
      .name("fanout-consumer-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-fanout-consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(consumerId)
        .requires(List.of(new InterfaceReference().id("fanout-int").version("1.0"))))));
    createDiscovery(consumerId, "http://mod-fanout-consumer:8081");

    awaitListenerAssignment();
    var cache = cacheManager.getCache(MODULE_BOOTSTRAP_CACHE);
    assertThat(cache).isNotNull();

    // warm both snapshots; the consumer's snapshot includes the provider as a required-interface provider
    getBootstrap(providerId);
    getBootstrap(consumerId);
    assertThat(cache.get(providerId)).as("provider snapshot warmed").isNotNull();
    assertThat(cache.get(consumerId)).as("consumer snapshot warmed").isNotNull();

    // a discovery change on the PROVIDER must, via the reverse-dependency fan-out, also evict the CONSUMER
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      kafkaTemplate.send(discoveryTopic, new DiscoveryEvent(providerId));
      assertThat(cache.get(providerId)).as("provider entry evicted by Kafka broadcast").isNull();
      assertThat(cache.get(consumerId)).as("dependent consumer entry evicted via fan-out").isNull();
    });
  }

  private void awaitEvictionByKafka(Cache cache, String publishedModuleId, String evictedKey) {
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      kafkaTemplate.send(discoveryTopic, new DiscoveryEvent(publishedModuleId));
      assertThat(cache.get(evictedKey)).as("entry must be evicted by the Kafka broadcast consumer").isNull();
    });
  }

  private void awaitListenerAssignment() {
    var container = kafkaListenerEndpointRegistry.getListenerContainer("bootstrap-cache-invalidation-listener");
    assertThat(container).as("bootstrap-cache invalidation listener container").isNotNull();
    await().atMost(TEN_SECONDS).until(() -> {
      var partitions = container.getAssignedPartitions();
      return partitions != null && !partitions.isEmpty();
    });
  }

  private void getBootstrap(String moduleId) throws Exception {
    mockMvc.perform(get("/modules/{id}", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());
  }

  private void createDiscovery(String moduleId, String location) throws Exception {
    var name = moduleId.substring(0, moduleId.lastIndexOf('-'));
    var version = moduleId.substring(moduleId.lastIndexOf('-') + 1);
    mockMvc.perform(post("/modules/{id}/discovery", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery(name, version, location))))
      .andExpect(status().isCreated());
  }

  private void postApplication(ApplicationDescriptor descriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }

  /** Ensures the discovery topic exists at start-up so the broadcast consumer can be assigned. */
  @TestConfiguration
  static class DiscoveryTopicConfiguration {

    @Bean
    public NewTopic bootstrapCacheDiscoveryTopic(@Value("${spring.kafka.topics.discovery}") String discoveryTopic) {
      return TopicBuilder.name(discoveryTopic).partitions(1).replicas(1).build();
    }
  }
}
