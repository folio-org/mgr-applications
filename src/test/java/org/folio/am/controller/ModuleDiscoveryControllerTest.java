package org.folio.am.controller;

import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestValues.moduleDiscoveries;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.folio.test.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.am.service.ModuleDiscoveryService;
import org.folio.security.integration.keycloak.client.KeycloakAuthClient;
import org.folio.security.integration.keycloak.model.TokenResponse;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@EnableKeycloakSecurity
@WebMvcTest(ModuleDiscoveryController.class)
@TestPropertySource(properties = "application.router.path-prefix=/")
@Import({ControllerTestConfiguration.class, ModuleDiscoveryController.class})
class ModuleDiscoveryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private KeycloakAuthClient keycloakAuthClient;
  @MockBean private ModuleDiscoveryService moduleDiscoveryService;

  @Test
  void getModuleDiscovery_positive() throws Exception {
    when(moduleDiscoveryService.get(MODULE_ID)).thenReturn(moduleDiscovery());

    mockMvc.perform(get("/modules/{id}/discovery", MODULE_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(moduleDiscovery()), true));
  }

  @Test
  void searchModuleDiscovery_positive() throws Exception {
    var query = "cql.allRecords = 1";
    when(moduleDiscoveryService.search(query, 20, 5)).thenReturn(moduleDiscoveries(moduleDiscovery()));

    mockMvc.perform(get("/modules/discovery")
        .queryParam("query", query)
        .queryParam("limit", "20")
        .queryParam("offset", "5")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleDiscovery())), true));
  }

  @Test
  void searchModuleDiscovery_positive_defaultPaginationParameter() throws Exception {
    var query = "cql.allRecords = 1";
    when(moduleDiscoveryService.search(query, 10, 0)).thenReturn(moduleDiscoveries(moduleDiscovery()));

    mockMvc.perform(get("/modules/discovery")
        .queryParam("query", query)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleDiscovery())), true));
  }

  @Test
  void createModuleDiscovery_positive_singleValueRequest() throws Exception {
    var request = moduleDiscovery().id(null);
    when(keycloakAuthClient.evaluatePermissions(anyMap(), anyString())).thenReturn(new TokenResponse());
    when(moduleDiscoveryService.create(MODULE_ID, request, OKAPI_AUTH_TOKEN)).thenReturn(moduleDiscovery());

    mockMvc.perform(post("/modules/{id}/discovery", MODULE_ID)
        .content(asJsonString(request))
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(moduleDiscovery()), true));
  }

  @Test
  void createModuleDiscovery_positive_batchRequest() throws Exception {
    var request = moduleDiscoveries(moduleDiscovery().id(null));
    when(keycloakAuthClient.evaluatePermissions(anyMap(), anyString())).thenReturn(new TokenResponse());
    when(moduleDiscoveryService.create(request, OKAPI_AUTH_TOKEN)).thenReturn(moduleDiscoveries(moduleDiscovery()));

    mockMvc.perform(post("/modules/discovery")
        .content(asJsonString(request))
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(moduleDiscoveries(moduleDiscovery())), true));
  }

  @Test
  void updateModuleDiscovery_positive() throws Exception {
    var request = moduleDiscovery();
    when(keycloakAuthClient.evaluatePermissions(anyMap(), anyString())).thenReturn(new TokenResponse());
    doNothing().when(moduleDiscoveryService).update(MODULE_ID, request, OKAPI_AUTH_TOKEN);

    mockMvc.perform(put("/modules/{id}/discovery", MODULE_ID)
        .content(asJsonString(request))
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void deleteModuleDiscovery_positive() throws Exception {
    when(keycloakAuthClient.evaluatePermissions(anyMap(), anyString())).thenReturn(new TokenResponse());
    doNothing().when(moduleDiscoveryService).delete(MODULE_ID, OKAPI_AUTH_TOKEN);

    mockMvc.perform(delete("/modules/{id}/discovery", MODULE_ID)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }
}
