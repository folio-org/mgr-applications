package org.folio.am.it;

import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.readString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.am.support.TestValues;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.extensions.impl.WireMockExtension;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
class ModuleBootstrapIT extends BaseIntegrationTest {

  @Autowired KeycloakProperties keycloakProperties;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/module-descriptor-provider/get-foo-module-9.9.9-descriptor.json"
  })
  void moduleBootstrap_positive() throws Exception {
    var wireMockUrl = WireMockExtension.getWireMockAdminClient().getWireMockUrl();
    var fooModule = TestValues.module("foo-module", "9.9.9", wireMockUrl + "/modules/foo-module-9.9.9");
    var applicationDescriptor =
      TestValues.applicationDescriptor("test-app-desired-perms", "1.0.0").addModulesItem(fooModule);

    mockMvc.perform(post("/applications").content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());

    mockMvc.perform(MockMvcRequestBuilders.get("/modules/foo-module-9.9.9")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(content().json(readString("json/module-bootstrap/bootstrap-foo-module-9.9.9.json")));
  }
}
