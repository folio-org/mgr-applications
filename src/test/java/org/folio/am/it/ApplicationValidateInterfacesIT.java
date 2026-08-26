package org.folio.am.it;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

import java.util.LinkedHashSet;
import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.dto.Module;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.extensions.EnablePostgres;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@IntegrationTest
@EnableKafka
@EnablePostgres
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.validation.default-mode=basic",
  "application.kong.enabled=false"
})
class ApplicationValidateInterfacesIT extends BaseBackendIntegrationTest {

  private static final String APP_MINIMAL_NAME = "app-minimal";
  private static final String APP_MINIMAL_VERSION = "1.0.0";
  private static final String APP_MINIMAL_ID = APP_MINIMAL_NAME + "-" + APP_MINIMAL_VERSION;
  private static final String APP_MINIMAL_MODULE_ID = "mod-minimal-1.0.0";

  private static final String APP_A_NAME = "app-a";
  private static final String APP_A_VERSION = "1.0.0";
  private static final String APP_A_ID = APP_A_NAME + "-" + APP_A_VERSION;
  private static final String APP_A_MODULE_ID = "mod-a-1.0.0";

  private static final String APP_B_NAME = "app-b";
  private static final String APP_B_VERSION = "1.0.0";
  private static final String APP_B_ID = APP_B_NAME + "-" + APP_B_VERSION;
  private static final String APP_B_MODULE_ID = "mod-b-1.0.0";

  @Test
  void validateInterfaces_positive_dependencyAlreadyRegistered_notInSubmittedSet() throws Exception {
    doPost("/applications", buildAppMinimal());
    doPost("/applications", buildAppA());
    doPost("/applications", buildAppB());

    var request = new ApplicationReferences().applicationIds(new LinkedHashSet<>(List.of(APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(MockMvcResultMatchers.status().isNoContent());
  }

  @Test
  void validateInterfaces_positive_allAppsSubmitted() throws Exception {
    doPost("/applications", buildAppMinimal());
    doPost("/applications", buildAppA());
    doPost("/applications", buildAppB());

    var request = new ApplicationReferences()
      .applicationIds(new LinkedHashSet<>(List.of(APP_MINIMAL_ID, APP_A_ID, APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(MockMvcResultMatchers.status().isNoContent());
  }

  @Test
  void validateInterfaces_negative_dependencyNotRegisteredAnywhere() throws Exception {
    doPost("/applications", buildAppB());

    var request = new ApplicationReferences().applicationIds(new LinkedHashSet<>(List.of(APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(MockMvcResultMatchers.status().isBadRequest())
      .andExpect(MockMvcResultMatchers.jsonPath(
        "$.errors[0].message", containsString("Application dependency not exist: name = " + APP_A_NAME)))
      .andExpect(MockMvcResultMatchers.jsonPath(
        "$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
      .andExpect(MockMvcResultMatchers.jsonPath(
        "$.total_records", is(1)));
  }

  @Test
  void validateInterfaces_negative_secondLevelDependencyNotRegistered() throws Exception {
    // app-minimal intentionally NOT registered — its interface won't be provided
    doPost("/applications", buildAppA());
    doPost("/applications", buildAppB());

    var request = new ApplicationReferences()
      .applicationIds(new LinkedHashSet<>(List.of(APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(MockMvcResultMatchers.status().isBadRequest())
      .andExpect(MockMvcResultMatchers.jsonPath("$.errors[0].message",
        containsString("Missing interfaces found for the applications")))
      .andExpect(MockMvcResultMatchers.jsonPath("$.errors[0].parameters[0].key",
        is(APP_B_ID)))
      .andExpect(MockMvcResultMatchers.jsonPath("$.errors[0].parameters[0].value",
        containsString("minimal-interface 1.0")))
      .andExpect(MockMvcResultMatchers.jsonPath("$.errors[0].type",
        is(RequestValidationException.class.getSimpleName())))
      .andExpect(MockMvcResultMatchers.jsonPath("$.total_records", is(1)));
  }

  // base app — provides "minimal-interface 1.0", no dependencies
  private static ApplicationDescriptor buildAppMinimal() {
    return new ApplicationDescriptor()
      .name(APP_MINIMAL_NAME)
      .version(APP_MINIMAL_VERSION)
      .modules(List.of(new Module().name("mod-minimal").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(APP_MINIMAL_MODULE_ID)
        .provides(List.of(new InterfaceDescriptor().id("minimal-interface").version("1.0")))));
  }

  // middle app — requires "minimal-interface 1.0", provides "a-interface 1.0", depends on app-minimal
  private static ApplicationDescriptor buildAppA() {
    return new ApplicationDescriptor()
      .name(APP_A_NAME)
      .version(APP_A_VERSION)
      .modules(List.of(new Module().name("mod-a").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(APP_A_MODULE_ID)
        .provides(List.of(new InterfaceDescriptor().id("a-interface").version("1.0")))
        .requires(List.of(new InterfaceReference().id("minimal-interface").version("1.0")))))
      .dependencies(List.of(new Dependency().name(APP_MINIMAL_NAME).version("^1.0.0")));
  }

  // requires "a-interface 1.0" and "minimal-interface 1.0" at module level
  private static ApplicationDescriptor buildAppB() {
    return new ApplicationDescriptor()
      .name(APP_B_NAME)
      .version(APP_B_VERSION)
      .modules(List.of(new Module().name("mod-b").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(APP_B_MODULE_ID)
        .requires(List.of(
          new InterfaceReference().id("a-interface").version("1.0"),
          new InterfaceReference().id("minimal-interface").version("1.0")))))
      .dependencies(List.of(new Dependency().name(APP_A_NAME).version("^1.0.0")));
  }
}
