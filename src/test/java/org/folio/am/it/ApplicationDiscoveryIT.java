package org.folio.am.it;

import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.KafkaEventAssertions.assertDiscoveryEvents;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_NAME;
import static org.folio.am.support.TestConstants.MODULE_BAR_URL;
import static org.folio.am.support.TestConstants.MODULE_BAR_VERSION;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_NAME;
import static org.folio.am.support.TestConstants.MODULE_FOO_URL;
import static org.folio.am.support.TestConstants.MODULE_FOO_VERSION;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestConstants.UI_MODULE_ID;
import static org.folio.am.support.TestValues.moduleDiscoveries;
import static org.folio.am.support.TestValues.moduleFooDiscovery;
import static org.folio.am.support.TestValues.uiModuleDiscovery;
import static org.folio.integration.kafka.producer.KafkaUtils.getEnvTopicName;
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
import org.folio.am.support.KafkaEventAssertions;
import org.folio.am.support.TestValues;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.utils.OkapiHeaders;
import org.folio.test.TestUtils;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.security.enabled=true",
  "application.keycloak.enabled=false"})
class ApplicationDiscoveryIT extends BaseIntegrationTest {

  @BeforeAll
  public static void setUp() {
    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void get_positive() throws Exception {
    doGet("/modules/{id}/discovery", MODULE_FOO_ID)
      .andExpect(content().json(asJsonString(TestValues.moduleFooDiscovery()), STRICT));
  }

  @Test
  void get_negative_notFound() throws Exception {
    var errorMessage = "Unable to find discovery of the module with id: " + MODULE_FOO_ID;
    attemptGet("/modules/{id}/discovery", MODULE_FOO_ID)
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive() throws Exception {
    var moduleDiscovery = TestValues.moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL);

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
      .andExpect(content().json(asJsonString(TestValues.moduleDiscoveries(moduleDiscovery)), STRICT));

    assertDiscoveryEvents(MODULE_BAR_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive_moduleNotFound() throws Exception {
    var moduleDiscovery = TestValues.moduleDiscovery("mod-unknown", "1.2.3", "http://test:80801");

    mockMvc.perform(post("/modules/{id}/discovery", moduleDiscovery.getId())
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Unable to find module with id: mod-unknown-1.2.3")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive_batchRequest() throws Exception {
    var moduleDiscoveries = TestValues.moduleDiscoveries(
      TestValues.moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL),
      TestValues.moduleDiscovery(MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL));

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

    KafkaEventAssertions.assertDiscoveryEvents(MODULE_BAR_ID, MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive_batchRequestModuleNotFound() throws Exception {
    var moduleDiscoveries = TestValues.moduleDiscoveries(
      TestValues.moduleDiscovery("mod-unknown", "1.2.3", "http://test:80801"));

    mockMvc.perform(post("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscoveries)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Modules are not found for ids: [mod-unknown-1.2.3]")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void update_positive() throws Exception {
    var newModuleDiscoveryUrl = "http://test-module-foo-updated:8080";

    var moduleDiscovery = TestValues.moduleFooDiscovery().location(newModuleDiscoveryUrl);
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
  void update_negative_noModule() throws Exception {
    var moduleDiscovery = TestValues.moduleDiscovery("mod-unknown", "1.2.3", "http://mod-unknwon:8081");
    mockMvc.perform(put("/modules/{id}/discovery", moduleDiscovery.getId())
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Unable to find module with id: mod-unknown-1.2.3")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  void update_negative_moduleIdDiffersFromThePathId() throws Exception {
    var id = "another-id";

    mockMvc.perform(put("/modules/{id}/discovery", id)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(TestUtils.asJsonString(TestValues.moduleFooDiscovery().id(id))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Module id in the discovery should be equal to: " + id)))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("id")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(id)))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  void update_negative_moduleIdDiffersFromArtifactId() throws Exception {
    var id = "another-id";

    mockMvc.perform(put("/modules/{id}/discovery", MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(TestValues.moduleFooDiscovery().id(id))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Module id must be based on the name and version")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("id")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(id)))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")));

    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void delete_positive() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = TestValues.moduleDiscoveries(TestValues.moduleBarDiscovery());
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
  void delete_positive_discoveryIsNotInIntegrationServices() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = TestValues.moduleDiscoveries(TestValues.moduleBarDiscovery());
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
  void delete_positive_noDiscovery() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());
    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-ui-it.sql")
  void create_positive_uiModule() throws Exception {
    var uiModuleDiscovery = uiModuleDiscovery();

    // No Okapi stubs needed - UI modules skip Okapi
    mockMvc.perform(post("/modules/{id}/discovery", UI_MODULE_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(uiModuleDiscovery)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(uiModuleDiscovery), STRICT));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries(uiModuleDiscovery)), STRICT));

    assertDiscoveryEvents(UI_MODULE_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-with-ui.sql")
  void update_positive_uiModule() throws Exception {
    var newModuleDiscoveryUrl = "http://test-ui-module-updated:8080";

    var uiModuleDiscovery = uiModuleDiscovery().location(newModuleDiscoveryUrl);

    // No Okapi stubs needed - UI modules skip Okapi
    mockMvc.perform(put("/modules/{id}/discovery", UI_MODULE_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(uiModuleDiscovery)))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/modules/{id}/discovery", UI_MODULE_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(uiModuleDiscovery), STRICT));

    assertDiscoveryEvents(UI_MODULE_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-with-ui.sql")
  void delete_positive_uiModule() throws Exception {
    // No Okapi stubs needed - UI modules skip Okapi
    mockMvc.perform(delete("/modules/{id}/discovery", UI_MODULE_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleFooDiscovery())), STRICT));

    assertDiscoveryEvents(UI_MODULE_ID);
  }
}
