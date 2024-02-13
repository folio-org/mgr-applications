package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.parseResponse;
import static org.folio.test.extensions.impl.WireMockExtension.getWireMockAdminClient;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.dto.Module;
import org.folio.am.support.TestValues;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@TestPropertySource(properties = {"application.validation.default-mode=basic"})
@IntegrationTest
@EnableKeycloakSecurity
@WireMockStub(scripts = "/wiremock/stubs/keycloak/obtain-token.json")
@Sql(scripts = "classpath:/sql/application-descriptor.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ApplicationIT extends BaseIntegrationTest {

  @Test
  void getById_positive() throws Exception {
    doGet("/applications/{id}", APPLICATION_ID)
      .andExpect(jsonPath("$.id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.name", is("test-app")))
      .andExpect(jsonPath("$.version", is("1.0.0")));
  }

  @Test
  void getByQuery_positive() throws Exception {
    doGet(get("/applications").queryParam("query", "id==\"test-app-1.0.0\""))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("test-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("1.0.0")));
  }

  @Test
  void getByQuery_positive_allValues() throws Exception {
    doGet(get("/applications")
      .queryParam("limit", "1"))
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/okapi/application/create-test-app.json"
  })
  void create_positive() throws Exception {
    var applicationDescriptor = TestValues.getApplicationDescriptor("test-module-1.1.0", "1.1.0");

    doPost("/applications", applicationDescriptor)
      .andExpect(jsonPath("$.id", is("test-0.1.1")))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")));
  }

  @Test
  void create_negative_no_descriptors() throws Exception {
    var module = new Module()
      .id("test-module")
      .version("1");
    var applicationDescriptor = new ApplicationDescriptor()
      .name("test")
      .version("0.1.1")
      .modules(List.of(module));

    attemptPost("/applications", applicationDescriptor)
      .andExpect(status().isBadRequest());
  }

  @Test
  void create_negative_not_all_descriptors() throws Exception {
    var module = new Module()
      .id("test-module")
      .version("1");
    var module2 = new Module()
      .id("test-module")
      .version("2");
    var moduleDescriptor = new ModuleDescriptor()
      .id("test-module-1")
      .name("test-module");
    var applicationDescriptor = new ApplicationDescriptor()
      .name("test")
      .version("0.1.1")
      .moduleDescriptors(List.of(moduleDescriptor))
      .modules(List.of(module, module2));

    attemptPost("/applications", applicationDescriptor)
      .andExpect(status().isBadRequest());
  }

  @Test
  void create_negative_invalidDependencyVersion() throws Exception {
    var descriptor = TestValues.getApplicationDescriptor("test-module-1.1.0", "1.1.0");
    var dependency = new Dependency().name("test-app").version("xxx");
    descriptor.addDependenciesItem(dependency);

    attemptPost("/applications", descriptor)
      .andExpectAll(argumentNotValidErr("Invalid semantic version or range(s): \"xxx\"",
        "dependencies[0].version", "xxx"));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/okapi/application/create-test-app.json"
  })
  void create_positive_with_dependency() throws Exception {

    var applicationDescriptor = new ApplicationDescriptor()
      .name("test")
      .version("0.1.1");

    var mvcResult = doPost("/applications", applicationDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")))
      .andReturn();

    var applicationDescriptor1 = parseResponse(mvcResult, ApplicationDescriptor.class);

    var dependency = new Dependency()
      .name(applicationDescriptor1.getName()).version(applicationDescriptor1.getVersion());

    var applicationDescriptor2 = new ApplicationDescriptor()
      .name("test2")
      .version("0.1.2")
      .dependencies(List.of(dependency));

    var mvcResult2 = doPost("/applications", applicationDescriptor2)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.name", is("test2")))
      .andExpect(jsonPath("$.version", is("0.1.2")))
      .andReturn();

    var result2 = parseResponse(mvcResult2, ApplicationDescriptor.class);
    assertThat(result2.getDependencies()).hasSize(1);
    assertThat(result2.getDependencies().get(0)).isEqualTo(dependency);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/module-descriptor-provider/get-bar-module-descriptor.json",
    "/wiremock/stubs/module-descriptor-provider/get-foo-module-descriptor.json",
    "/wiremock/stubs/okapi/application/create-bar-module.json",
    "/wiremock/stubs/okapi/application/create-foo-module.json"
  })
  void create_positive_moduleDescriptorsByUrl() throws Exception {
    var baseUrl = getWireMockAdminClient().getWireMockUrl();
    var fooModule = TestValues.module("foo-module", "1.0.0", baseUrl + "/modules/foo-module-1.0.0");
    var barModule = TestValues.module("bar-module", "1.0.0", baseUrl + "/modules/bar-module-1.0.0");

    var applicationDescriptor = TestValues.applicationDescriptor("test", "0.1.1")
      .addModulesItem(fooModule).addUiModulesItem(barModule);

    doPost("/applications", applicationDescriptor)
      .andExpect(content().json(asJsonString(applicationDescriptor), true));

    var fullApplicationDescriptor = TestValues.applicationDescriptor("test", "0.1.1")
      .addModulesItem(fooModule)
      .addModuleDescriptorsItem(new ModuleDescriptor().id("foo-module-1.0.0").name("foo-module"))
      .addUiModulesItem(barModule)
      .addUiModuleDescriptorsItem(new ModuleDescriptor().id("bar-module-1.0.0").name("bar-module"));

    doGet("/applications/test-0.1.1?full=true", applicationDescriptor)
      .andExpect(content().json(asJsonString(fullApplicationDescriptor), true));
  }

  @Test
  void validate_positive() throws Exception {
    var descriptor = new ApplicationDescriptor()
      .name("validate-test")
      .version("0.1.1")
      .addModulesItem(new Module().name("test-m1").version("0.0.1"))
      .addModuleDescriptorsItem(new ModuleDescriptor()
        .id("test-m1-0.0.1")
        .name("test-module")
        .addProvidesItem(new InterfaceDescriptor().id("test-interface").version("0.1")));

    attemptPost("/applications/validate", descriptor).andExpect(status().isNoContent());
  }

  @Test
  void validate_positive_existingApplication() throws Exception {
    var descriptor = new ApplicationDescriptor()
      .name("test-app")
      .version("2.0.0")
      .addModulesItem(new Module().name("mod-foo").version("1.0.1"))
      .addModulesItem(new Module().name("mod-bar").version("1.0.0"))
      .addModuleDescriptorsItem(new ModuleDescriptor().id("mod-foo-1.0.1").name("foo")
        .addProvidesItem(new InterfaceDescriptor().id("int-foo").version("1.1")))
      .addModuleDescriptorsItem(new ModuleDescriptor().id("mod-bar-1.0.0").name("bar")
        .addProvidesItem(new InterfaceDescriptor().id("int-bar").version("1.0"))
        .addRequiresItem(new InterfaceReference().id("int-foo").version("1.0")));

    attemptPost("/applications/validate", descriptor).andExpect(status().isNoContent());
  }

  @Test
  void validate_negative() throws Exception {
    var descriptor = new ApplicationDescriptor()
      .name("validate-test")
      .version("0.1.1")
      .addModulesItem(new Module().name("test-module").version("1.0.1"))
      .addModulesItem(new Module().name("test-module2").version("1.0.0"))
      .addModuleDescriptorsItem(new ModuleDescriptor()
        .id("test-m1-0.0.1")
        .name("test-module")
        .addProvidesItem(new InterfaceDescriptor().id("test-interface").version("0.1"))
        .addRequiresItem(new InterfaceReference().id("test2-interface").version("0.1")));

    attemptPost("/applications/validate", descriptor)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(
        "Module descriptors are not found in application descriptor: validate-test-0.1.1")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("moduleDescriptors")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("[test-module-1.0.1, test-module2-1.0.0]")));
  }
}
