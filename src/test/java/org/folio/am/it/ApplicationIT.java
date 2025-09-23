package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.getApplicationDescriptor;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.parseResponse;
import static org.folio.test.extensions.impl.WireMockExtension.getWireMockAdminClient;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@TestPropertySource(properties = {"application.validation.default-mode=basic"})
@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@Sql(scripts = {
  "classpath:/sql/application-descriptor.sql",
  "classpath:/sql/application-descriptor-with-versions.sql"}, executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ApplicationIT extends BaseIntegrationTest {

  @Autowired private KeycloakProperties keycloakProperties;

  @Test
  void getById_positive() throws Exception {
    mockMvc.perform(get("/applications/{id}", APPLICATION_ID)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.name", is("test-app")))
      .andExpect(jsonPath("$.version", is("1.0.0")));
  }

  @Test
  void getByQuery_positive() throws Exception {
    mockMvc.perform(get("/applications").queryParam("query", "id==\"test-app-1.0.0\"")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("test-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("1.0.0")));
  }

  @Test
  void getByQuery_positive_allValues() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("limit", "1"))
      .andExpect(jsonPath("$.totalRecords", is(9)));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/stubs/okapi/application/create-test-app.json"
  })
  void create_positive() throws Exception {
    var applicationDescriptor = getApplicationDescriptor("test-module-1.1.0", "1.1.0");

    mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(jsonPath("$.id", is("test-0.1.1")))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")));

    mockMvc.perform(get("/applications/{id}", "test-0.1.1")
        .queryParam("full", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is("test-0.1.1")))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")))
      .andExpect(jsonPath("$.moduleDescriptors[0].metadata.user.type", is("system")))
      .andExpect(jsonPath("$.moduleDescriptors[0].metadata.user.permissions",
        is(List.of("test.permission1", "test.permission2"))));
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

    mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
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
      .description("test-module");
    var applicationDescriptor = new ApplicationDescriptor()
      .name("test")
      .version("0.1.1")
      .moduleDescriptors(List.of(moduleDescriptor))
      .modules(List.of(module, module2));

    mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  void create_negative_invalidDependencyVersion() throws Exception {
    var descriptor = getApplicationDescriptor("test-module-1.1.0", "1.1.0");
    var dependency = new Dependency().name("test-app").version("xxx");
    descriptor.addDependenciesItem(dependency);

    mockMvc.perform(post("/applications")
        .content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
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

    var mvcResult = mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
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

    var mvcResult2 = mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor2))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.name", is("test2")))
      .andExpect(jsonPath("$.version", is("0.1.2")))
      .andReturn();

    var result2 = parseResponse(mvcResult2, ApplicationDescriptor.class);
    assertThat(result2.getDependencies()).hasSize(1);
    assertThat(result2.getDependencies().getFirst()).isEqualTo(dependency);
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

    var mvcResult1 = mockMvc.perform(post("/applications").content(asJsonString(applicationDescriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andReturn();

    var appResult = parseResponse(mvcResult1, ApplicationDescriptor.class);
    assertThat(appResult).isEqualTo(applicationDescriptor);

    var fullApplicationDescriptor = TestValues.applicationDescriptor("test", "0.1.1")
      .addModulesItem(fooModule)
      .addModuleDescriptorsItem(new ModuleDescriptor().id("foo-module-1.0.0").description("foo-module"))
      .addUiModulesItem(barModule)
      .addUiModuleDescriptorsItem(new ModuleDescriptor().id("bar-module-1.0.0").description("bar-module"));

    var mvcResult2 = mockMvc.perform(get("/applications/test-0.1.1?full=true")
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andReturn();

    var fullAppResult = parseResponse(mvcResult2, ApplicationDescriptor.class);
    assertThat(fullAppResult).isEqualTo(fullApplicationDescriptor);
  }

  @Test
  void validate_positive() throws Exception {
    var descriptor = new ApplicationDescriptor()
      .name("validate-test")
      .version("0.1.1")
      .addModulesItem(new Module().name("test-m1").version("0.0.1"))
      .addModuleDescriptorsItem(new ModuleDescriptor()
        .id("test-m1-0.0.1")
        .description("test-module")
        .addProvidesItem(new InterfaceDescriptor().id("test-interface").version("0.1")));

    mockMvc.perform(post("/applications/validate")
        .content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void validate_positive_existingApplication() throws Exception {
    var descriptor = new ApplicationDescriptor()
      .name("test-app")
      .version("2.0.0")
      .addModulesItem(new Module().name("mod-foo").version("1.0.1"))
      .addModulesItem(new Module().name("mod-bar").version("1.0.0"))
      .addModuleDescriptorsItem(new ModuleDescriptor().id("mod-foo-1.0.1").description("foo")
        .addProvidesItem(new InterfaceDescriptor().id("int-foo").version("1.1")))
      .addModuleDescriptorsItem(new ModuleDescriptor().id("mod-bar-1.0.0").description("bar")
        .addProvidesItem(new InterfaceDescriptor().id("int-bar").version("1.0"))
        .addRequiresItem(new InterfaceReference().id("int-foo").version("1.0")));

    mockMvc.perform(post("/applications/validate")
        .content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
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
        .description("test-module")
        .addProvidesItem(new InterfaceDescriptor().id("test-interface").version("0.1"))
        .addRequiresItem(new InterfaceReference().id("test2-interface").version("0.1")));

    mockMvc.perform(post("/applications/validate")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, generateAccessToken(keycloakProperties)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(
        "Module descriptors are not found in application descriptor: validate-test-0.1.1")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("moduleDescriptors")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("[test-module-1.0.1, test-module2-1.0.0]")));
  }

  @Test
  void getByAppName_latest_parameter_positive() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("latest", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.1")));
  }

  @Test
  void getByAppName_latest_parameter_multiple_with_desc_version_sorting() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("latest", "2")
        .queryParam("order", "desc")
        .queryParam("orderBy", "version"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[1].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[1].version", is("9.0.0-SNAPSHOT.4012")));
  }

  @Test
  void getByAppName_latest_parameter_multiple_with_asc_version_sorting() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("latest", "2")
        .queryParam("order", "asc")
        .queryParam("orderBy", "version"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.0-SNAPSHOT.4012")))
      .andExpect(jsonPath("$.applicationDescriptors[1].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[1].version", is("9.0.1")));
  }

  @Test
  void getByAppName_versions_sort_desc() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("order", "desc")
        .queryParam("orderBy", "version"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[1].version", is("9.0.0-SNAPSHOT.4012")))
      .andExpect(jsonPath("$.applicationDescriptors[2].version", is("9.0.0-SNAPSHOT.3456")))
      .andExpect(jsonPath("$.applicationDescriptors[3].version", is("8.1.0")))
      .andExpect(jsonPath("$.applicationDescriptors[4].version", is("8.1.0-SNAPSHOT.2245")))
      .andExpect(jsonPath("$.applicationDescriptors[5].version", is("8.0.1")));
  }

  @Test
  void getByAppName_versions_sort_asc() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("order", "asc")
        .queryParam("orderBy", "version"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("8.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[1].version", is("8.1.0-SNAPSHOT.2245")))
      .andExpect(jsonPath("$.applicationDescriptors[2].version", is("8.1.0")))
      .andExpect(jsonPath("$.applicationDescriptors[3].version", is("9.0.0-SNAPSHOT.3456")))
      .andExpect(jsonPath("$.applicationDescriptors[4].version", is("9.0.0-SNAPSHOT.4012")))
      .andExpect(jsonPath("$.applicationDescriptors[5].version", is("9.0.1")));
  }

  @Test
  void getByAppName_application_not_found() throws Exception {
    // Test filter parameter to find applications containing specific ID pattern
    mockMvc.perform(get("/applications")
        .queryParam("appName", "other"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)));
  }

  @Test
  void getByAppName_invalid_latest_parameter() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("latest", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message",
        is("getApplicationsByQuery.latest must be greater than or equal to 1")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void getByAdvancedFiltering_missing_appName_parameter() throws Exception {
    // Test error when using advanced filtering parameters without appName
    mockMvc.perform(get("/applications")
        .queryParam("latest", "1"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Filter parameter `appName` is required when"
        + " using `latest`, `preRelease`, `order`, `orderBy` for version-specific filtering")))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void getByAppName_orderBy_id_ascending() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("orderBy", "id")
        .queryParam("order", "asc"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is("my-app-8.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[1].id", is("my-app-8.1.0")))
      .andExpect(jsonPath("$.applicationDescriptors[2].id", is("my-app-8.1.0-SNAPSHOT.2245")))
      .andExpect(jsonPath("$.applicationDescriptors[3].id", is("my-app-9.0.0-SNAPSHOT.3456")))
      .andExpect(jsonPath("$.applicationDescriptors[4].id", is("my-app-9.0.0-SNAPSHOT.4012")))
      .andExpect(jsonPath("$.applicationDescriptors[5].id", is("my-app-9.0.1")));
  }

  @Test
  void getByAppName_orderBy_id_descending() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("orderBy", "id")
        .queryParam("order", "desc"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is("my-app-9.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[1].id", is("my-app-9.0.0-SNAPSHOT.4012")))
      .andExpect(jsonPath("$.applicationDescriptors[2].id", is("my-app-9.0.0-SNAPSHOT.3456")))
      .andExpect(jsonPath("$.applicationDescriptors[3].id", is("my-app-8.1.0-SNAPSHOT.2245")))
      .andExpect(jsonPath("$.applicationDescriptors[4].id", is("my-app-8.1.0")))
      .andExpect(jsonPath("$.applicationDescriptors[5].id", is("my-app-8.0.1")));
  }

  @Test
  void getByAppName_with_full_true() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("full", "true")
        .queryParam("latest", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[0].moduleDescriptors").isArray())
      .andExpect(jsonPath("$.applicationDescriptors[0].moduleDescriptors").isNotEmpty());
  }

  @Test
  void getByAppName_with_full_false() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("full", "false")
        .queryParam("latest", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is("my-app")))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.1")))
      .andExpect(jsonPath("$.applicationDescriptors[0].moduleDescriptors").isEmpty());
  }

  @Test
  void getByAppName_nonExistent_with_full_false() throws Exception {
    mockMvc.perform(get("/applications")
        .queryParam("appName", "non-existent-app")
        .queryParam("full", "false"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.applicationDescriptors").isArray())
      .andExpect(jsonPath("$.applicationDescriptors").isEmpty());
  }

  @Test
  void getByAppName_preRelease_true() throws Exception {
    // Test preRelease=true (default) - should include all versions
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[*].version",
        containsInAnyOrder("8.0.1", "8.1.0", "8.1.0-SNAPSHOT.2245", "9.0.0-SNAPSHOT.3456", "9.0.0-SNAPSHOT.4012", "9.0.1")));
  }

  @Test
  void getByAppName_preRelease_false() throws Exception {
    // Test preRelease=false - should only include stable releases
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "false"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(3)))
      .andExpect(jsonPath("$.applicationDescriptors[*].version",
        containsInAnyOrder("8.0.1", "8.1.0", "9.0.1")));
  }

  @Test
  void getByAppName_preRelease_only() throws Exception {
    // Test preRelease=only - should only include pre-release versions
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "only"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(3)))
      .andExpect(jsonPath("$.applicationDescriptors[*].version",
        containsInAnyOrder("8.1.0-SNAPSHOT.2245", "9.0.0-SNAPSHOT.3456", "9.0.0-SNAPSHOT.4012")));
  }

  @Test
  void getByAppName_preRelease_with_latest() throws Exception {
    // Test preRelease=false with latest=2 - should get latest 2 stable releases
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "false")
        .queryParam("latest", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.applicationDescriptors[*].version",
        containsInAnyOrder("9.0.1", "8.1.0")));
  }

  @Test
  void getByAppName_preRelease_only_with_latest() throws Exception {
    // Test preRelease=only with latest=1 - should get latest pre-release
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "only")
        .queryParam("latest", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is("9.0.0-SNAPSHOT.4012")));
  }

  @Test
  void getByAppName_preRelease_invalid_value() throws Exception {
    // Test invalid preRelease value - should return bad request
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "invalid"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Invalid preRelease value: invalid. Valid values are: true, false, only")))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void getByAppName_preRelease_null_defaults_to_true() throws Exception {
    // Test that omitting preRelease parameter defaults to including all versions
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(6)))
      .andExpect(jsonPath("$.applicationDescriptors[*].version",
        containsInAnyOrder("8.0.1", "8.1.0", "8.1.0-SNAPSHOT.2245", "9.0.0-SNAPSHOT.3456", "9.0.0-SNAPSHOT.4012", "9.0.1")));
  }

  @Test
  void getByAppName_preRelease_case_sensitivity() throws Exception {
    // Test that preRelease values are case-sensitive
    mockMvc.perform(get("/applications")
        .queryParam("appName", "my-app")
        .queryParam("preRelease", "TRUE"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Invalid preRelease value: TRUE. Valid values are: true, false, only")));
  }
}
