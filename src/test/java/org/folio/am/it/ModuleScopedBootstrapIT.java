package org.folio.am.it;

import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.dto.ModuleBootstrapRequest;
import org.folio.am.support.base.BaseIntegrationTest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@TestPropertySource(properties = {"application.okapi.enabled=false", "application.kong.enabled=false"})
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ModuleScopedBootstrapIT extends BaseIntegrationTest {

  @Autowired KeycloakProperties keycloakProperties;

  @Test
  void postModuleBootstrap_ingress_returnsModuleOnly() throws Exception {
    postApplication(new ApplicationDescriptor()
      .name("ingress-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-ingress").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-ingress-1.0.0")
        .provides(List.of(new InterfaceDescriptor().id("ingress-int").version("1.0").interfaceType("multiple"))))));

    var request = new ModuleBootstrapRequest().type(ModuleBootstrapRequest.TypeEnum.INGRESS);

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-ingress-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ingress.module.moduleId").value("mod-ingress-1.0.0"))
      .andExpect(jsonPath("$.ingress.requiredModules").isEmpty())
      .andExpect(jsonPath("$.egress").doesNotExist());
  }

  @Test
  void postModuleBootstrap_egress_returnsScopedProviders() throws Exception {
    postProviderAndConsumerApps();

    var request = new ModuleBootstrapRequest()
      .type(ModuleBootstrapRequest.TypeEnum.EGRESS)
      .applicationIds(List.of("consumer-app-1.0.0", "provider-app-1.0.0"));

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.egress.found").value(true))
      .andExpect(jsonPath("$.egress.bootstrap.module.moduleId").value("mod-consumer-1.0.0"))
      .andExpect(jsonPath("$.egress.bootstrap.requiredModules[0].moduleId").value("mod-provider-1.0.0"))
      .andExpect(jsonPath("$.ingress").doesNotExist());
  }

  @Test
  void postModuleBootstrap_egress_moduleOutsideScope_returnsNotFound() throws Exception {
    postProviderAndConsumerApps();

    var request = new ModuleBootstrapRequest()
      .type(ModuleBootstrapRequest.TypeEnum.EGRESS)
      .applicationIds(List.of("provider-app-1.0.0"));

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.egress.found").value(false))
      .andExpect(jsonPath("$.egress.bootstrap").doesNotExist());
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-bootstrap-shared-provider.sql")
  void postModuleBootstrap_egress_sharedProviderReachableViaSecondApp_found() throws Exception {
    var request = new ModuleBootstrapRequest()
      .type(ModuleBootstrapRequest.TypeEnum.EGRESS)
      .applicationIds(List.of("app-consumer-1.0.0", "app-prov-b-1.0.0"));

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.egress.found").value(true))
      .andExpect(jsonPath("$.egress.bootstrap.requiredModules[0].moduleId").value("mod-provider-1.0.0"));
  }

  private void postProviderAndConsumerApps() throws Exception {
    postApplication(new ApplicationDescriptor()
      .name("provider-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-provider").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-provider-1.0.0")
        .provides(List.of(new InterfaceDescriptor().id("shared-int").version("1.0").interfaceType("multiple"))))));

    postApplication(new ApplicationDescriptor()
      .name("consumer-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-consumer-1.0.0")
        .requires(List.of(new InterfaceReference().id("shared-int").version("1.0"))))));

    mockMvc.perform(post("/modules/{id}/discovery", "mod-provider-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery("mod-provider", "1.0.0", "http://mod-provider:8081"))))
      .andExpect(status().isCreated());
  }

  private void postApplication(ApplicationDescriptor applicationDescriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }

  @Test
  void postModuleBootstrap_egress_multiversion_selectsInScopeProviderVersion() throws Exception {
    postProviderApp("provider-app", "1.0.0", "mod-provider-1.0.0", "http://mod-provider-1:8081");
    postProviderApp("provider-app", "2.0.0", "mod-provider-2.0.0", "http://mod-provider-2:8081");

    postApplication(new ApplicationDescriptor()
      .name("consumer-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-consumer").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-consumer-1.0.0")
        .requires(List.of(new InterfaceReference().id("shared-int").version("1.0"))))));

    var request = new ModuleBootstrapRequest()
      .type(ModuleBootstrapRequest.TypeEnum.EGRESS)
      .applicationIds(List.of("consumer-app-1.0.0", "provider-app-1.0.0"));

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.egress.found").value(true))
      .andExpect(jsonPath("$.egress.bootstrap.requiredModules[0].moduleId").value("mod-provider-1.0.0"))
      .andExpect(jsonPath("$.egress.bootstrap.requiredModules[0].location")
        .value("http://mod-provider-1:8081"));
  }

  private void postProviderApp(String appName, String appVersion, String moduleId, String location) throws Exception {
    var name = moduleId.substring(0, moduleId.lastIndexOf('-'));
    var version = moduleId.substring(moduleId.lastIndexOf('-') + 1);
    postApplication(new ApplicationDescriptor()
      .name(appName).version(appVersion)
      .modules(List.of(new Module().name(name).version(version)))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(moduleId)
        .provides(List.of(new InterfaceDescriptor().id("shared-int").version("1.0").interfaceType("multiple"))))));

    mockMvc.perform(post("/modules/{id}/discovery", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(org.folio.am.support.TestValues.moduleDiscovery(name, version, location))))
      .andExpect(status().isCreated());
  }
}
