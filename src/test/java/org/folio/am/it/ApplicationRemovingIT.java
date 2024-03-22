package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.repository.UiModuleRepository;
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
@Sql(scripts = "classpath:/sql/application-descriptor-for-removing.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ApplicationRemovingIT extends BaseIntegrationTest {

  private static final String MODULE_FOO_ID = "mod-foo-1.0.0";
  private static final String MODULE_BAR_ID = "mod-bar-1.0.0";

  private static final String UI_APPLICATION_ID = "test-app-3.0.0";
  private static final String UI_MODULE_FOO_ID = "ui-foo-1.0.0";
  private static final String UI_MODULE_BAR_ID = "ui-bar-1.0.0";

  @Autowired private ModuleRepository moduleRepository;
  @Autowired private UiModuleRepository uiModuleRepository;
  @Autowired private KeycloakProperties keycloakProperties;

  @BeforeAll
  public static void setUp() {
    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @Test
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement-not-exist.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application/delete-mod-bar-1.0.0.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application-discovery/delete-module-bar-discovery.json")
  void delete_positive_modulesPartiallyRemoved() throws Exception {
    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(3)));

    var found = moduleRepository.findAllById(Set.of(MODULE_FOO_ID, MODULE_BAR_ID));
    assertThat(found).hasSize(1)
      .allMatch(module -> module.getId().equals(MODULE_FOO_ID));

    KafkaEventAssertions.assertDiscoveryEvents(MODULE_BAR_ID);
  }

  @Test
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement-not-exist.json")
  void delete_positive_uiModulesPartiallyRemoved() throws Exception {
    mockMvc.perform(delete("/applications/{id}", UI_APPLICATION_ID)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isNoContent());

    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(3)));

    var found = uiModuleRepository.findAllById(Set.of(UI_MODULE_FOO_ID, UI_MODULE_BAR_ID));
    assertThat(found).hasSize(1)
      .allMatch(module -> module.getId().equals(UI_MODULE_FOO_ID));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application/delete-test-app.json")
  void delete_negative_entitlementExist() throws Exception {
    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isConflict());

    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(4)));
  }

  @Test
  @WireMockStub(scripts = "/wiremock/stubs/mte/get-entitlement-service-exception.json")
  @WireMockStub(scripts = "/wiremock/stubs/okapi/application/delete-test-app.json")
  void delete_negative_serviceException() throws Exception {
    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isBadRequest());

    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(4)));
  }

  @Test
  void delete_negative_entityNotFound() throws Exception {
    mockMvc.perform(delete("/applications/{id}", "UNKNOWN_ID")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isNotFound());

    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(4)));
  }
}
