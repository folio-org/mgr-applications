package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.integration.kafka.producer.KafkaUtils.getEnvTopicName;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.folio.am.domain.entity.ArtifactEntity;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.support.KafkaEventAssertions;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@Sql(scripts = "classpath:/sql/application-cleanup.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ApplicationCleanupIT extends BaseIntegrationTest {

  private static final String CLEANED_APPLICATION_ID = "test-app-1.0.0";
  private static final String SKIPPED_APPLICATION_ID = "test-app-2.0.0";
  private static final String CLEANED_MODULE_ID = "mod-bar-1.0.0";
  private static final String SHARED_MODULE_ID = "mod-foo-1.0.0";
  private static final String SKIPPED_MODULE_ID = "mod-bar-1.0.1";

  @Autowired private ModuleRepository moduleRepository;
  @Autowired private KeycloakProperties keycloakProperties;

  @BeforeAll
  static void setUp() {
    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @Test
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement-application-cleanup-not-installed.json")
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement-application-cleanup.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application/delete-mod-bar-1.0.0.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application-discovery/delete-module-bar-discovery.json")
  void cleanup_positive_removesUnusedAppsAndSkipsEntitledApps() throws Exception {
    mockMvc.perform(post("/applications/cleanup")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.inspected", is(2)))
      .andExpect(jsonPath("$.cleaned", is(1)))
      .andExpect(jsonPath("$.skipped", is(1)))
      .andExpect(jsonPath("$.failed", is(0)))
      .andExpect(jsonPath("$.cleanedIds", contains(CLEANED_APPLICATION_ID)))
      .andExpect(jsonPath("$.skippedIds", contains(SKIPPED_APPLICATION_ID)))
      .andExpect(jsonPath("$.failedIds", empty()));

    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(SKIPPED_APPLICATION_ID)));

    var found = moduleRepository.findAllById(Set.of(CLEANED_MODULE_ID, SHARED_MODULE_ID, SKIPPED_MODULE_ID));
    assertThat(found).hasSize(2)
      .extracting(ArtifactEntity::getId)
      .containsExactlyInAnyOrder(SHARED_MODULE_ID, SKIPPED_MODULE_ID);

    KafkaEventAssertions.assertDiscoveryEvents(CLEANED_MODULE_ID);
  }
}
