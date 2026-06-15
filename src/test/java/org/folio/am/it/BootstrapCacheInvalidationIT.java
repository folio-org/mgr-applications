package org.folio.am.it;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end proof that a discovery change invalidates the module-bootstrap cache while the cache is
 * ENABLED. The broad IT suite runs with {@code application.bootstrap-cache.enabled=false} (it-profile);
 * this IT opts back in via {@link TestPropertySource}, which outranks the profile file, so the Caffeine
 * cache manager is active. After warming the cache, a discovery update must flip the observed location
 * within the await window — only possible if the cache was evicted (in-process afterCommit and/or the
 * Kafka broadcast).
 */
@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@TestPropertySource(properties = {
  "application.okapi.enabled=false",
  "application.kong.enabled=false",
  "application.bootstrap-cache.enabled=true"
})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class BootstrapCacheInvalidationIT extends BaseIntegrationTest {

  @Autowired KeycloakProperties keycloakProperties;

  @Test
  void discoveryChange_invalidatesBootstrapCache() throws Exception {
    postApplication(new ApplicationDescriptor()
      .name("cache-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-cache").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-cache-1.0.0")
        .provides(List.of(new InterfaceDescriptor().id("cache-int").version("1.0").interfaceType("multiple"))))));

    createDiscovery("mod-cache-1.0.0", "http://mod-cache-1:8081");

    // warm the cache
    getBootstrap("mod-cache-1.0.0").andExpect(jsonPath("$.module.location").value("http://mod-cache-1:8081"));

    // change discovery -> in-process afterCommit evict (and/or Kafka broadcast)
    updateDiscovery("mod-cache-1.0.0", "http://mod-cache-2:8081");

    await().atMost(TEN_SECONDS).untilAsserted(() ->
      getBootstrap("mod-cache-1.0.0").andExpect(jsonPath("$.module.location").value("http://mod-cache-2:8081")));
  }

  private ResultActions getBootstrap(String moduleId) throws Exception {
    return mockMvc.perform(get("/modules/{id}", moduleId)
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

  private void updateDiscovery(String moduleId, String location) throws Exception {
    var name = moduleId.substring(0, moduleId.lastIndexOf('-'));
    var version = moduleId.substring(moduleId.lastIndexOf('-') + 1);
    mockMvc.perform(put("/modules/{id}/discovery", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery(name, version, location))))
      .andExpect(status().isNoContent());
  }

  private void postApplication(ApplicationDescriptor descriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }
}
