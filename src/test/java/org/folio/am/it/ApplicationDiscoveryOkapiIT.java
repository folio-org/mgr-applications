package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.KafkaEventAssertions.assertDiscoveryEvents;
import static org.folio.am.support.KafkaEventAssertions.assertNoDiscoveryEvents;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestConstants.UI_MODULE_ID;
import static org.folio.am.support.TestValues.moduleBarDiscovery;
import static org.folio.am.support.TestValues.moduleDiscoveries;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.am.support.TestValues.moduleFooDiscovery;
import static org.folio.am.support.TestValues.uiModuleDiscovery;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.folio.test.TestUtils.asJsonString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.utils.OkapiHeaders;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@EnableOkapiSecurity
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {"application.okapi.enabled=true", "application.kong.enabled=false"})
class ApplicationDiscoveryOkapiIT extends BaseIntegrationTest {

  @BeforeAll
  public static void setUp(@Autowired ApplicationContext applicationContext) {
    assertThat(applicationContext.containsBean("kongAdminClient")).isFalse();
    assertThat(applicationContext.containsBean("okapiClient")).isTrue();

    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void getByQuery_positive() throws Exception {
    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery(), moduleFooDiscovery());
    mockMvc.perform(get("/modules/discovery", MODULE_BAR_ID)
        .queryParam("query", "cql.allRecords=1 sortBy id/sort.ascending")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/mod-authtoken/verify-token-create-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-bar-discovery.json"
  })
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive() throws Exception {
    var moduleDiscovery = moduleBarDiscovery();

    mockMvc.perform(post("/modules/{id}/discovery", MODULE_BAR_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(moduleDiscovery), STRICT));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleDiscovery)), STRICT));

    assertDiscoveryEvents(MODULE_BAR_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-create-module-discovery.json")
  void create_positive_moduleNotFound() throws Exception {
    var moduleDiscovery = moduleDiscovery("mod-unknown", "1.2.3", "http://test:80801");

    mockMvc.perform(post("/modules/{id}/discovery", moduleDiscovery.getId())
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Unable to find module with id: mod-unknown-1.2.3")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-create-modules-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery-not-found.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-foo-discovery.json"
  })
  void create_positive_batchRequest() throws Exception {
    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery(), moduleFooDiscovery());

    mockMvc.perform(post("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscoveries)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    assertDiscoveryEvents(MODULE_BAR_ID, MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-create-modules-discovery.json")
  void create_positive_batchRequestModuleNotFound() throws Exception {
    var moduleDiscoveries = moduleDiscoveries(moduleDiscovery("mod-unknown", "1.2.3", "http://test:80801"));

    mockMvc.perform(post("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscoveries)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Modules are not found for ids: [mod-unknown-1.2.3]")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    assertNoDiscoveryEvents();
  }

  @Test
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-foo-discovery-updated.json"
  })
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void update_positive() throws Exception {
    var newModuleDiscoveryUrl = "http://test-module-foo-updated:8080";
    var moduleDiscovery = moduleFooDiscovery().location(newModuleDiscoveryUrl);

    mockMvc.perform(put("/modules/{id}/discovery", MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/modules/{id}/discovery", MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscovery), STRICT));

    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json")
  void update_negative_noModule() throws Exception {
    var moduleDiscovery = moduleDiscovery("mod-unknown", "1.2.3", "http://mod-unknwon:8081");
    mockMvc.perform(put("/modules/{id}/discovery", moduleDiscovery.getId())
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Unable to find module with id: mod-unknown-1.2.3")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery.json",
  })
  void delete_positive() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery());
    mockMvc.perform(get("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery-not-found.json",
  })
  void delete_positive_discoveryIsNotFoundInOkapi() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery());
    mockMvc.perform(get("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), STRICT));

    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-ui-it.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-create-module-discovery.json")
  void create_positive_uiModuleSkipsOkapi() throws Exception {
    var uiModuleDiscovery = uiModuleDiscovery();

    // No Okapi discovery stubs needed - UI modules skip Okapi
    mockMvc.perform(post("/modules/{id}/discovery", UI_MODULE_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(uiModuleDiscovery)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(uiModuleDiscovery), STRICT));

    assertDiscoveryEvents(UI_MODULE_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-with-ui.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json")
  void delete_positive_uiModuleSkipsOkapi() throws Exception {
    // No Okapi discovery stubs needed - UI modules skip Okapi
    mockMvc.perform(delete("/modules/{id}/discovery", UI_MODULE_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    assertDiscoveryEvents(UI_MODULE_ID);
  }
}
