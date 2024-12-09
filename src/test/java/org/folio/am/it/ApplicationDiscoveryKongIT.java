package org.folio.am.it;

import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.KafkaEventAssertions.assertDiscoveryEvents;
import static org.folio.am.support.KafkaEventAssertions.assertNoDiscoveryEvents;
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
import static org.folio.am.support.TestValues.moduleBarDiscovery;
import static org.folio.am.support.TestValues.moduleDiscoveries;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.am.support.TestValues.moduleFooDiscovery;
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
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.utils.OkapiHeaders;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.model.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {"application.okapi.enabled=false", "application.kong.enabled=true"})
class ApplicationDiscoveryKongIT extends BaseIntegrationTest {

  @Autowired private KongAdminClient kongAdminClient;

  @BeforeAll
  public static void setUp(@Autowired ApplicationContext applicationContext) {
    assertThat(applicationContext.containsBean("folioKongAdminClient")).isTrue();
    assertThat(applicationContext.containsBean("folioKongGatewayService")).isTrue();
    assertThat(applicationContext.containsBean("kongDiscoveryListener")).isTrue();
    assertThat(applicationContext.containsBean("okapiClient")).isFalse();

    fakeKafkaConsumer.registerTopic(getEnvTopicName(DISCOVERY_DESTINATION), DiscoveryEvent.class);
  }

  @AfterEach
  void tearDown() {
    deleteService(MODULE_FOO_ID);
    deleteService(MODULE_BAR_ID);
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
      .andExpect(content().json(asJsonString(moduleDiscoveries), true));
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void create_positive() throws Exception {
    var moduleDiscovery = moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL);

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
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleDiscovery)), true));

    assertThatKongHasServiceWithUrl(MODULE_BAR_ID, MODULE_BAR_URL);

    var routes = kongAdminClient.getServiceRoutes(MODULE_BAR_ID, null);
    assertThat(routes.getData()).hasSize(1);
    assertThat(routes.getData().get(0).getExpression()).contains("(http.path == \"/foo/bar\" && http.method == \"POST\")");

    assertDiscoveryEvents(MODULE_BAR_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
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
  void create_positive_batchRequest() throws Exception {
    var moduleDiscoveries = moduleDiscoveries(
      moduleDiscovery(MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL),
      moduleDiscovery(MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL));

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

    assertDiscoveryEvents(MODULE_BAR_ID, MODULE_FOO_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
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
  @Sql(scripts = "classpath:/sql/module-discoveries.sql")
  void update_positive() throws Exception {
    var newModuleDiscoveryUrl = "http://test-module-foo-updated:8080";
    var moduleDiscovery = moduleDiscovery(MODULE_FOO_NAME, MODULE_FOO_VERSION, newModuleDiscoveryUrl);

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
  void delete_positive() throws Exception {
    kongAdminClient.upsertService(MODULE_BAR_ID, new Service().name(MODULE_BAR_ID).url(MODULE_BAR_URL));
    kongAdminClient.upsertService(MODULE_FOO_ID, new Service().name(MODULE_FOO_ID).url(MODULE_FOO_URL));

    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery());
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
  void delete_positive_discoveryInfoIsNotFound() throws Exception {
    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_FOO_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    var moduleDiscoveries = moduleDiscoveries(moduleBarDiscovery());
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

    assertThatThrownBy(() -> kongAdminClient.getService(MODULE_FOO_ID)).isInstanceOf(NotFound.class);
    assertDiscoveryEvents(MODULE_FOO_ID);
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

  private void deleteService(String serviceId) {
    try {
      kongAdminClient.getServiceRoutes(serviceId, null)
        .forEach(route -> kongAdminClient.deleteRoute(serviceId, route.getId()));
    } catch (NotFound nf) {
      // Do nothing
    }
    kongAdminClient.deleteService(serviceId);
  }
}
