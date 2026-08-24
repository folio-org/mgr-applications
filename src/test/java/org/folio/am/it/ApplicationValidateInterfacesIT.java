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

  private static final String PLATFORM_APP_NAME = "app-platform-minimal";
  private static final String PLATFORM_APP_VERSION = "1.0.0";
  private static final String PLATFORM_APP_ID = PLATFORM_APP_NAME + "-" + PLATFORM_APP_VERSION;
  private static final String PLATFORM_MODULE_ID = "mod-configuration-1.0.0";

  private static final String APP_B_NAME = "app-b";
  private static final String APP_B_VERSION = "1.0.0";
  private static final String APP_B_ID = APP_B_NAME + "-" + APP_B_VERSION;
  private static final String APP_B_MODULE_ID = "mod-b-1.0.0";

  @Test
  void validateInterfaces_positive_dependencyAlreadyRegistered_notInSubmittedSet() throws Exception {
    var platformDescriptor = buildPlatformApp();
    doPost("/applications", platformDescriptor);

    var appDescriptor = buildAppB();
    doPost("/applications", appDescriptor);

    var request = new ApplicationReferences().applicationIds(new LinkedHashSet<>(List.of(APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
  }

  @Test
  void validateInterfaces_positive_allAppsSubmitted() throws Exception {
    doPost("/applications", buildPlatformApp());
    doPost("/applications", buildAppB());

    var request = new ApplicationReferences()
      .applicationIds(new LinkedHashSet<>(List.of(PLATFORM_APP_ID, APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
  }

  @Test
  void validateInterfaces_negative_dependencyNotRegisteredAnywhere() throws Exception {
    var appDescriptor = buildAppB();
    doPost("/applications", appDescriptor);

    var request = new ApplicationReferences().applicationIds(new LinkedHashSet<>(List.of(APP_B_ID)));

    attemptPost("/applications/validate-interfaces", request)
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
        "$.errors[0].message", containsString("Application dependency not exist: name = " + PLATFORM_APP_NAME)))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
        "$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
        "$.total_records", is(1)));
  }

  private static ApplicationDescriptor buildPlatformApp() {
    return new ApplicationDescriptor()
      .name(PLATFORM_APP_NAME)
      .version(PLATFORM_APP_VERSION)
      .modules(List.of(new Module().name("mod-configuration").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(PLATFORM_MODULE_ID)
        .provides(List.of(new InterfaceDescriptor().id("configuration").version("1.0")))));
  }

  private static ApplicationDescriptor buildAppB() {
    return new ApplicationDescriptor()
      .name(APP_B_NAME)
      .version(APP_B_VERSION)
      .modules(List.of(new Module().name("mod-b").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id(APP_B_MODULE_ID)
        .requires(List.of(new InterfaceReference().id("configuration").version("1.0")))))
      .dependencies(List.of(new Dependency().name(PLATFORM_APP_NAME).version("^1.0.0")));
  }
}
