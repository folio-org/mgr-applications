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
import org.folio.am.domain.dto.ModuleBootstrapEndpoint;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
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
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@TestPropertySource(properties = "application.apigw.enabled=false")
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
        .interfaces(List.of(
          new ModuleBootstrapInterface().id("consumer-api").version("1.0").interfaceType("multiple"))))
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

  @Test
  void egressBootstrap_positive_providerInTwoApplicationVersions_highestApplicationVersion() throws Exception {
    // an unchanged module is part of every version of its application; 10.0.0 is higher than 2.0.0 only by version
    postProviderApplication("provider-app", "2.0.0", "mod-provider", "1.0.0", dashboard());
    postProviderApplication("provider-app", "10.0.0", "mod-provider", "1.0.0", dashboard());
    postDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081");
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-1.0.0", "provider-app-10.0.0", "http://mod-provider:8081", dashboardBootstrap())));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-2.0.0", "provider-app-10.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_descriptorRewrittenByLaterApplicationVersion_returnsNewHandlers() throws Exception {
    postProviderApplication("provider-app", "1.0.0", "mod-provider", "1.0.0", dashboard());
    postProviderApplication("provider-app", "2.0.0", "mod-provider", "1.0.0",
      dashboard().addHandlersItem(new RoutingEntry().addMethodsItem("GET").pathPattern("/dashboard")));
    postDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081");
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-1.0.0", "provider-app-2.0.0", "http://mod-provider:8081",
        dashboardBootstrap().addEndpointsItem(new ModuleBootstrapEndpoint().addMethodsItem("GET")
          .pathPattern("/dashboard")))));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-1.0.0", "provider-app-2.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_multipleInterfaceProvidedByDifferentModules_allReturned() throws Exception {
    postProviderApplication("provider-a-app", "1.0.0", "mod-provider-a", "1.0.0", dashboard());
    postProviderApplication("provider-b-app", "1.0.0", "mod-provider-b", "3.0.0", dashboard());
    postDiscovery("mod-provider-a", "1.0.0", "http://mod-provider-a:8081");
    postDiscovery("mod-provider-b", "3.0.0", "http://mod-provider-b:8081");
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-a-1.0.0", "provider-a-app-1.0.0", "http://mod-provider-a:8081", dashboardBootstrap()),
      provider("mod-provider-b-3.0.0", "provider-b-app-1.0.0", "http://mod-provider-b:8081", dashboardBootstrap())));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-b-app-1.0.0", "provider-a-app-1.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_twoProviderVersions_highestVersion() throws Exception {
    postProviderApplication("provider-app", "1.0.0", "mod-provider", "1.0.0", dashboard());
    postProviderApplication("provider-app", "2.0.0", "mod-provider", "2.0.0", dashboard());
    postDiscovery("mod-provider", "1.0.0", "http://mod-provider-1:8081");
    postDiscovery("mod-provider", "2.0.0", "http://mod-provider-2:8081");
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-2.0.0", "provider-app-2.0.0", "http://mod-provider-2:8081", dashboardBootstrap())));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-1.0.0", "provider-app-2.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_onlyRequiredInterfacesReturned() throws Exception {
    postProviderApplication("provider-app", "1.0.0", "mod-provider", "1.0.0",
      dashboard(), new InterfaceDescriptor().id("unrelated").version("1.0").interfaceType("multiple"));
    postDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081");
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-1.0.0", "provider-app-1.0.0", "http://mod-provider:8081", dashboardBootstrap())));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-1.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_interfacesSortedById() throws Exception {
    postProviderApplication("provider-app", "1.0.0", "mod-provider", "1.0.0",
      new InterfaceDescriptor().id("b-api").version("1.0").interfaceType("multiple"),
      new InterfaceDescriptor().id("a-api").version("1.0").interfaceType("multiple"));
    postDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081");
    postConsumerApplication(List.of(
      new InterfaceReference().id("b-api").version("1.0"), new InterfaceReference().id("a-api").version("1.0")),
      List.of());

    var expected = new EgressBootstrap().requiredModules(List.of(
      provider("mod-provider-1.0.0", "provider-app-1.0.0", "http://mod-provider:8081",
        new ModuleBootstrapInterface().id("a-api").version("1.0").interfaceType("multiple"),
        new ModuleBootstrapInterface().id("b-api").version("1.0").interfaceType("multiple"))));

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-1.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), STRICT));
  }

  @Test
  void egressBootstrap_positive_providerWithoutDiscoveryExcluded() throws Exception {
    postProviderApplication("provider-app", "1.0.0", "mod-provider", "1.0.0", dashboard());
    postConsumerApplication(List.of(new InterfaceReference().id("dashboard").version("2.0")), List.of());

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", "provider-app-1.0.0")
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(new EgressBootstrap().requiredModules(List.of())), STRICT));
  }

  @Test
  void egressBootstrap_negative_blankApplicationId() throws Exception {
    postConsumerApplication(List.of(), List.of());

    postEgressBootstrap("consumer-1.0.0", "test-app-1.0.0", " ")
      .andExpect(status().isBadRequest());
  }

  @Test
  void egressBootstrap_negative_uiModule() throws Exception {
    var uiApp = new ApplicationDescriptor()
      .name("ui-app").version("1.0.0")
      .uiModules(List.of(new Module().name("ui-module").version("1.0.0")))
      .uiModuleDescriptors(List.of(new ModuleDescriptor().id("ui-module-1.0.0")));
    postApplication(uiApp);

    postEgressBootstrap("ui-module-1.0.0", "ui-app-1.0.0")
      .andExpect(status().isNotFound());
  }

  private static InterfaceDescriptor dashboard() {
    return new InterfaceDescriptor().id("dashboard").version("2.0").interfaceType("multiple");
  }

  private static ModuleBootstrapInterface dashboardBootstrap() {
    return new ModuleBootstrapInterface().id("dashboard").version("2.0").interfaceType("multiple");
  }

  private static ModuleBootstrapDiscovery provider(String moduleId, String applicationId, String location,
    ModuleBootstrapInterface... interfaces) {
    return new ModuleBootstrapDiscovery()
      .moduleId(moduleId)
      .applicationId(applicationId)
      .location(location)
      .systemUserRequired(false)
      .interfaces(List.of(interfaces));
  }

  private void postProviderApplication(String appName, String appVersion, String moduleName, String moduleVersion,
    InterfaceDescriptor... provides) throws Exception {
    postApplication(new ApplicationDescriptor()
      .name(appName).version(appVersion)
      .modules(List.of(new Module().name(moduleName).version(moduleVersion)))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(moduleName + "-" + moduleVersion)
        .provides(List.of(provides)))));
  }

  private void postConsumerApplication(List<InterfaceReference> requires, List<InterfaceReference> optional)
    throws Exception {
    postApplication(new ApplicationDescriptor()
      .name("test-app").version("1.0.0")
      .modules(List.of(new Module().name("consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("consumer-1.0.0")
        .requires(requires)
        .optional(optional))));
  }

  private void postDiscovery(String moduleName, String moduleVersion, String location) throws Exception {
    mockMvc.perform(post("/modules/{id}/discovery", moduleName + "-" + moduleVersion)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery(moduleName, moduleVersion, location))))
      .andExpect(status().isCreated());
  }

  private ResultActions postEgressBootstrap(String moduleId, String... applicationIds) throws Exception {
    return mockMvc.perform(post("/modules/{id}/bootstrap", moduleId)
      .header(TOKEN, generateAccessToken(keycloakProperties))
      .contentType(APPLICATION_JSON)
      .content(asJsonString(new EgressBootstrapRequest().applicationIds(List.of(applicationIds)))));
  }

  private void postApplication(ApplicationDescriptor applicationDescriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }
}
