package org.folio.am.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_INTERFACE_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_INTERFACE_ID;
import static org.folio.am.support.TestConstants.ROLE_USER;
import static org.folio.am.support.TestValues.moduleBootstrap;
import static org.folio.am.support.TestValues.moduleBootstrapDiscovery;
import static org.folio.test.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.folio.am.domain.dto.EgressBootstrap;
import org.folio.am.domain.dto.EgressBootstrapRequest;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.service.ModuleBootstrapService;
import org.folio.test.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(ModuleBootstrapController.class)
@WithMockUser(username = "test-user", roles = ROLE_USER)
@TestPropertySource(properties = "application.router.path-prefix=/")
@Import({ControllerTestConfiguration.class, ModuleBootstrapController.class})
class ModuleBootstrapControllerTest {

  private static final String ENDPOINT_PATH = "/modules/{id}";
  private static final String BOOTSTRAP_PATH = "/modules/{id}/bootstrap";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ModuleBootstrapService service;

  @Test
  void get_positive() throws Exception {
    var module = moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    var requiredModules = moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    var expectedModuleBootstrap = moduleBootstrap(module, requiredModules);

    when(service.getById(MODULE_FOO_ID)).thenReturn(expectedModuleBootstrap);

    var mvcResult = mockMvc.perform(get(ENDPOINT_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ModuleBootstrap.class);
    assertThat(actual).isEqualTo(expectedModuleBootstrap);
  }

  @Test
  void get_negative() throws Exception {
    var errorMessage = "Module not found by id: " + MODULE_FOO_ID;
    when(service.getById(MODULE_FOO_ID)).thenThrow(new EntityNotFoundException(errorMessage));
    mockMvc.perform(get(ENDPOINT_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  void getModuleIngressBootstrap_positive() throws Exception {
    var module = moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    var expected = new ModuleBootstrap().module(module).requiredModules(List.of());
    when(service.getIngressBootstrap(MODULE_FOO_ID)).thenReturn(expected);

    var mvcResult = mockMvc.perform(get(BOOTSTRAP_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(parseResponse(mvcResult, ModuleBootstrap.class)).isEqualTo(expected);
  }

  @Test
  void getModuleEgressBootstrap_positive() throws Exception {
    var requiredModule = moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    var expected = new EgressBootstrap().requiredModules(List.of(requiredModule));
    when(service.getEgressBootstrap(MODULE_FOO_ID, List.of("test-app-1.0.0"))).thenReturn(expected);

    var mvcResult = mockMvc.perform(post(BOOTSTRAP_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .content(TestUtils.asJsonString(new EgressBootstrapRequest().applicationIds(List.of("test-app-1.0.0")))))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(parseResponse(mvcResult, EgressBootstrap.class)).isEqualTo(expected);
  }

  @Test
  void getModuleEgressBootstrap_negative_emptyApplicationIds() throws Exception {
    mockMvc.perform(post(BOOTSTRAP_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .content(TestUtils.asJsonString(new EgressBootstrapRequest().applicationIds(List.of()))))
      .andExpect(status().isBadRequest());
  }

  @Test
  void getModuleEgressBootstrap_negative_notFound() throws Exception {
    var errorMessage = "Module not found by id: " + MODULE_FOO_ID;
    when(service.getEgressBootstrap(MODULE_FOO_ID, List.of("test-app-1.0.0")))
      .thenThrow(new EntityNotFoundException(errorMessage));

    mockMvc.perform(post(BOOTSTRAP_PATH, MODULE_FOO_ID)
        .contentType(APPLICATION_JSON)
        .content(TestUtils.asJsonString(new EgressBootstrapRequest().applicationIds(List.of("test-app-1.0.0")))))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")));
  }
}
