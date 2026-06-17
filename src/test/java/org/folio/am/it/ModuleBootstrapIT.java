package org.folio.am.it;

import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.module;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.readString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.EgressBootstrap;
import org.folio.am.domain.dto.EgressBootstrapRequest;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@TestPropertySource(properties = {"application.okapi.enabled=false", "application.kong.enabled=false"})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ModuleBootstrapIT extends BaseIntegrationTest {

  @Autowired KeycloakProperties keycloakProperties;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/module-descriptor-provider/get-foo-module-9.9.9-descriptor.json"
  })
  void moduleBootstrap_positive() throws Exception {
    var wireMockUrl = wireMockAdminClient.getWireMockUrl();
    var fooModule = module("foo-module", "9.9.9", wireMockUrl + "/modules/foo-module-9.9.9");
    var applicationDescriptor = applicationDescriptor("test-app", "1.0.0").addModulesItem(fooModule);

    postApplication(applicationDescriptor);

    mockMvc.perform(get("/modules/foo-module-9.9.9")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(content().json(readString("json/module-bootstrap/bootstrap-foo-module-9.9.9.json"), STRICT));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/module-descriptor-provider/get-baz-module-descriptor.json"
  })
  void moduleBootstrap_positive_systemUserRequired() throws Exception {
    var wireMockUrl = wireMockAdminClient.getWireMockUrl();
    var fooModule = module("baz-module", "1.0.0", wireMockUrl + "/modules/baz-module-1.0.0");
    var applicationDescriptor = applicationDescriptor("test-app", "1.0.0").addModulesItem(fooModule);

    postApplication(applicationDescriptor);

    mockMvc.perform(get("/modules/baz-module-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(content().json(readString("json/module-bootstrap/bootstrap-baz-module-1.0.0.json"), STRICT));
  }

  @Test
  void moduleBootstrap_positive_sameInterfaceInProvidesAndOptional() throws Exception {
    var dashboardProviderApp = new ApplicationDescriptor()
      .name("provider-app")
      .version("1.0.0")
      .modules(List.of(new Module().name("mod-provider").version("1.0.0")))
      .moduleDescriptors(List.of(
        new ModuleDescriptor()
          .id("mod-provider-1.0.0")
          .provides(List.of(new InterfaceDescriptor()
            .id("dashboard")
            .version("2.0")
            .interfaceType("multiple")))));

    postApplication(dashboardProviderApp);

    mockMvc.perform(post("/modules/{id}/discovery", "mod-provider-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081"))))
      .andExpect(status().isCreated());

    var dashboardProviderAndConsumerApp = new ApplicationDescriptor()
      .name("test-app")
      .version("1.0.0")
      .modules(List.of(new Module().name("provider-and-consumer").version("1.0.0")))
      .moduleDescriptors(List.of(
        new ModuleDescriptor()
          .id("provider-and-consumer-1.0.0")
          .optional(List.of(new InterfaceReference().id("dashboard").version("2.0")))
          .provides(List.of(new InterfaceDescriptor()
            .id("dashboard")
            .version("2.0")
            .interfaceType("multiple")))));

    postApplication(dashboardProviderAndConsumerApp);

    var expectedBootstrapResponse = new ModuleBootstrap()
      .module(new ModuleBootstrapDiscovery()
        .moduleId("provider-and-consumer-1.0.0")
        .applicationId("test-app-1.0.0")
        .systemUserRequired(false)
        .interfaces(List.of(new ModuleBootstrapInterface()
          .id("dashboard")
          .version("2.0")
          .interfaceType("multiple"))))
      .requiredModules(List.of(new ModuleBootstrapDiscovery()
        .moduleId("mod-provider-1.0.0")
        .applicationId("provider-app-1.0.0")
        .location("http://mod-provider:8081")
        .systemUserRequired(false)
        .interfaces(List.of(new ModuleBootstrapInterface()
          .id("dashboard")
          .version("2.0")
          .interfaceType("multiple")))));

    mockMvc.perform(get("/modules/provider-and-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expectedBootstrapResponse), STRICT));
  }

  @Test
  void ingressBootstrap_positive() throws Exception {
    // Consumer both provides its own interface and requires another; ingress must return only its own routes.
    var consumerApp = new ApplicationDescriptor()
      .name("test-app").version("1.0.0")
      .modules(List.of(new Module().name("consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("consumer-1.0.0")
        .requires(List.of(new InterfaceReference().id("dashboard").version("2.0")))
        .provides(List.of(new InterfaceDescriptor().id("consumer-api").version("1.0").interfaceType("multiple")))));
    postApplication(consumerApp);

    var expected = new ModuleBootstrap()
      .module(new ModuleBootstrapDiscovery()
        .moduleId("consumer-1.0.0")
        .applicationId("test-app-1.0.0")
        .systemUserRequired(false)
        .interfaces(List.of(new ModuleBootstrapInterface().id("consumer-api").version("1.0").interfaceType("multiple"))))
      .requiredModules(List.of());

    mockMvc.perform(get("/modules/{id}/bootstrap", "consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_scoped() throws Exception {
    var providerApp = new ApplicationDescriptor()
      .name("provider-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-provider").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-provider-1.0.0")
        .provides(List.of(new InterfaceDescriptor().id("dashboard").version("2.0").interfaceType("multiple")))));
    postApplication(providerApp);

    mockMvc.perform(post("/modules/{id}/discovery", "mod-provider-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081"))))
      .andExpect(status().isCreated());

    var consumerApp = new ApplicationDescriptor()
      .name("test-app").version("1.0.0")
      .modules(List.of(new Module().name("consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("consumer-1.0.0")
        .requires(List.of(new InterfaceReference().id("dashboard").version("2.0")))));
    postApplication(consumerApp);

    // In full scope: provider is resolved.
    var expectedInScope = new EgressBootstrap().requiredModules(List.of(new ModuleBootstrapDiscovery()
      .moduleId("mod-provider-1.0.0")
      .applicationId("provider-app-1.0.0")
      .location("http://mod-provider:8081")
      .systemUserRequired(false)
      .interfaces(List.of(new ModuleBootstrapInterface().id("dashboard").version("2.0").interfaceType("multiple")))));

    mockMvc.perform(post("/modules/{id}/bootstrap", "consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(new EgressBootstrapRequest()
          .applicationIds(List.of("provider-app-1.0.0", "test-app-1.0.0")))))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expectedInScope), STRICT));

    // Provider out of scope: empty requiredModules (consumer itself still in scope).
    var expectedOutOfScope = new EgressBootstrap().requiredModules(List.of());
    mockMvc.perform(post("/modules/{id}/bootstrap", "consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(new EgressBootstrapRequest().applicationIds(List.of("test-app-1.0.0")))))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expectedOutOfScope), STRICT));
  }

  @Test
  void egressBootstrap_negative_moduleNotInScope() throws Exception {
    var consumerApp = new ApplicationDescriptor()
      .name("test-app").version("1.0.0")
      .modules(List.of(new Module().name("consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor().id("consumer-1.0.0")));
    postApplication(consumerApp);

    mockMvc.perform(post("/modules/{id}/bootstrap", "consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(new EgressBootstrapRequest().applicationIds(List.of("other-app-1.0.0")))))
      .andExpect(status().isNotFound());
  }

  private void postApplication(ApplicationDescriptor applicationDescriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }
}
