package org.folio.am.it;

import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.folio.test.TestUtils.asJsonString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.FeignException.NotFound;
import java.net.URL;
import lombok.SneakyThrows;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.support.KafkaEventAssertions;
import org.folio.am.support.TestValues;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.utils.OkapiHeaders;
import org.folio.test.TestUtils;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.model.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@EnableOkapiSecurity
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {"application.okapi.enabled=true", "application.kong.enabled=true"})
class ApplicationDiscoveryIT extends BaseIntegrationTest {

  @Autowired private KongAdminClient kongAdminClient;

  @BeforeAll
  public static void setUp() {
    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @AfterEach
  void tearDown() {
    kongAdminClient.deleteService(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void get_positive() throws Exception {
    doGet("/modules/{id}/discovery", MODULE_FOO_ID)
      .andExpect(content().json(asJsonString(TestValues.moduleFooDiscovery()), true));
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
  @WireMockStub(scripts = {
    "/wiremock/stubs/mod-authtoken/verify-token-create-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-bar-discovery.json"
  })
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive() throws Exception {
    var moduleDiscovery = TestValues.moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL);

    mockMvc.perform(post("/modules/{id}/discovery", MODULE_BAR_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscovery)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(moduleDiscovery), true));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(TestValues.moduleDiscoveries(moduleDiscovery)), true));

    assertThatKongHasServiceWithUrl(MODULE_BAR_ID, MODULE_BAR_URL);

    assertDiscoveryEvents(MODULE_BAR_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-create-module-discovery.json")
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
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-create-modules-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery-not-found.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-bar-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-foo-discovery.json"
  })
  void create_positive_batchRequest() throws Exception {
    var moduleDiscoveries = TestValues.moduleDiscoveries(
      TestValues.moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL),
      TestValues.moduleDiscovery(MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL));

    mockMvc.perform(post("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(moduleDiscoveries)))
      .andExpect(status().isCreated())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    assertThatKongHasServiceWithUrl(MODULE_BAR_ID, MODULE_BAR_URL);
    assertThatKongHasServiceWithUrl(MODULE_FOO_ID, MODULE_FOO_URL);

    KafkaEventAssertions.assertDiscoveryEvents(MODULE_BAR_ID, MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-create-modules-discovery.json")
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
  @WireMockStub(scripts = {
    "/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/create-test-module-foo-discovery-updated.json"
  })
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void update_positive() throws Exception {
    var newModuleDiscoveryUrl = "http://test-module-foo-updated:8080";
    kongAdminClient.upsertService(MODULE_FOO_ID, new Service().url(MODULE_FOO_URL));

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
      .andExpect(content().json(asJsonString(moduleDiscovery), true));

    assertThatKongHasServiceWithUrl(MODULE_FOO_ID, newModuleDiscoveryUrl);
    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json")
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
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json")
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
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-update-module-discovery.json")
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
  @WireMockStub(scripts = {
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery.json",
    "/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json"
  })
  void delete_positive() throws Exception {
    kongAdminClient.upsertService(MODULE_FOO_ID, new Service().url(MODULE_FOO_URL));
    kongAdminClient.upsertService(MODULE_BAR_ID, new Service().url(MODULE_BAR_URL));

    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = TestValues.moduleDiscoveries(TestValues.moduleBarDiscovery());
    mockMvc.perform(get("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    assertThat(kongAdminClient.getService(MODULE_BAR_ID)).isNotNull();
    assertThatThrownBy(() -> kongAdminClient.getService(MODULE_FOO_ID)).isInstanceOf(NotFound.class);
    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  @WireMockStub({
    "/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/get-test-module-foo-discovery.json",
    "/wiremock/stubs/okapi/application-discovery/delete-test-module-foo-discovery-not-found.json",
  })
  void delete_positive_discoveryIsNotInIntegrationServices() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = TestValues.moduleDiscoveries(TestValues.moduleBarDiscovery());
    mockMvc.perform(get("/modules/discovery")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    mockMvc.perform(get("/applications/{id}/discovery", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));

    assertDiscoveryEvents(MODULE_FOO_ID);
  }

  @Test
  @WireMockStub("/wiremock/stubs/mod-authtoken/verify-token-delete-module-discovery.json")
  void delete_positive_noDiscovery() throws Exception {
    doDelete("/modules/{id}/discovery", MODULE_FOO_ID);
    assertThatThrownBy(() -> kongAdminClient.getService(MODULE_FOO_ID)).isInstanceOf(NotFound.class);
    KafkaEventAssertions.assertNoDiscoveryEvents();
  }

  @SneakyThrows
  private void assertThatKongHasServiceWithUrl(String serviceId, String urlString) {
    var url = new URL(urlString);
    var adminClientService = kongAdminClient.getService(serviceId);
    assertThat(adminClientService).isNotNull().satisfies(service -> {
      assertThat(service.getProtocol()).isEqualTo(url.getProtocol());
      assertThat(service.getHost()).isEqualTo(url.getHost());
      assertThat(service.getPort()).isEqualTo(url.getPort());
      assertThat(service.getPath()).isEqualTo(stripToNull(url.getPath()));
    });
  }
}
