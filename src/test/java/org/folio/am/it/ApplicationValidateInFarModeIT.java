package org.folio.am.it;

import static java.lang.String.format;
import static org.folio.test.TestUtils.copy;
import static org.folio.test.TestUtils.parse;
import static org.folio.test.TestUtils.readString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDescriptorsValidation;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.extensions.EnablePostgres;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@Log4j2
@EnableKafka
@EnablePostgres
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.far-mode.enabled=true",
  "application.kong.enabled=false",
  "application.okapi.enabled=false"
})
class ApplicationValidateInFarModeIT  extends BaseBackendIntegrationTest {

  private static final ApplicationDescriptor APP_PLATFORM_MINIMAL =
    parse(readString("json/application-descriptor/app-platform-minimal.json"), ApplicationDescriptor.class);
  private static final ApplicationDescriptor APP_PLATFORM_COMPLETE =
    parse(readString("json/application-descriptor/app-platform-complete.json"), ApplicationDescriptor.class);
  private static final ApplicationDescriptor APP_EHOLDINGS =
    parse(readString("json/application-descriptor/app-eholdings.json"), ApplicationDescriptor.class);
  private static final ApplicationDescriptor APP_INN_REACH =
    parse(readString("json/application-descriptor/app-inn-reach.json"), ApplicationDescriptor.class);

  @Test
  void validateDescriptors_positive_allInRequest() throws Exception {
    var req = validationReq(APP_PLATFORM_MINIMAL, APP_PLATFORM_COMPLETE, APP_EHOLDINGS, APP_INN_REACH);

    attemptPost("/applications/validate-descriptors", req)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()", is(4)))
      .andExpect(jsonPath("$.[0]", is(APP_EHOLDINGS.getId())))
      .andExpect(jsonPath("$.[1]", is(APP_INN_REACH.getId())))
      .andExpect(jsonPath("$.[2]", is(APP_PLATFORM_COMPLETE.getId())))
      .andExpect(jsonPath("$.[3]", is(APP_PLATFORM_MINIMAL.getId())));
  }

  @Test
  void validateDescriptors_positive_oneStoredAnotherInRequest() throws Exception {
    doPost("/applications", APP_PLATFORM_MINIMAL);

    var req = validationReq(APP_PLATFORM_COMPLETE, APP_EHOLDINGS, APP_INN_REACH);

    attemptPost("/applications/validate-descriptors", req)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()", is(4)))
      .andExpect(jsonPath("$.[0]", is(APP_EHOLDINGS.getId())))
      .andExpect(jsonPath("$.[1]", is(APP_INN_REACH.getId())))
      .andExpect(jsonPath("$.[2]", is(APP_PLATFORM_COMPLETE.getId())))
      .andExpect(jsonPath("$.[3]", is(APP_PLATFORM_MINIMAL.getId())));
  }

  @Test
  void validateDescriptors_positive_twoStoredAnotherInRequest() throws Exception {
    doPost("/applications", APP_PLATFORM_MINIMAL);
    doPost("/applications", APP_PLATFORM_COMPLETE);

    var req = validationReq(APP_EHOLDINGS, APP_INN_REACH);

    attemptPost("/applications/validate-descriptors", req)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()", is(4)))
      .andExpect(jsonPath("$.[0]", is(APP_EHOLDINGS.getId())))
      .andExpect(jsonPath("$.[1]", is(APP_INN_REACH.getId())))
      .andExpect(jsonPath("$.[2]", is(APP_PLATFORM_COMPLETE.getId())))
      .andExpect(jsonPath("$.[3]", is(APP_PLATFORM_MINIMAL.getId())));
  }

  @Test
  void validateDescriptors_negative_duplicatedApplication() throws Exception {
    var anotherAppPlatformMinimal = copy(APP_PLATFORM_MINIMAL)
      .id(APP_PLATFORM_MINIMAL.getName() + "-0.0.1-test")
      .version("0.0.1-test");

    var req = validationReq(APP_PLATFORM_MINIMAL, anotherAppPlatformMinimal, APP_PLATFORM_COMPLETE);

    attemptPost("/applications/validate-descriptors", req)
      .andExpectAll(validationErr(
        RequestValidationException.class.getSimpleName(),
        "Duplicate application descriptor with the same name in the request",
        "name", APP_PLATFORM_MINIMAL.getName()));
  }

  @Test
  void validateDescriptors_negative_unsatisfiedDependency() throws Exception {
    var req = validationReq(APP_PLATFORM_MINIMAL, APP_EHOLDINGS, APP_INN_REACH);

    attemptPost("/applications/validate-descriptors", req)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message",
        containsString("Cannot find application which satisfies the dependency")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("dependencyName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(APP_PLATFORM_COMPLETE.getName())))
      .andExpect(jsonPath("$.errors[0].parameters[1].key", is("dependencyVersion")))
      .andExpect(jsonPath("$.errors[0].parameters[1].value", is("^3.0.0-SNAPSHOT")))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  @Test
  void validateDescriptors_negative_versionMismatch() throws Exception {
    var appEholdings = copy(APP_EHOLDINGS);
    var dependencyVersion = "0.0.1-test";
    appEholdings.getDependencies().getFirst().setVersion(dependencyVersion);

    var req = validationReq(APP_PLATFORM_MINIMAL, APP_PLATFORM_COMPLETE, appEholdings, APP_INN_REACH);

    attemptPost("/applications/validate-descriptors", req)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message",
        containsString(format("Dependency version range '%s' "
          + "is not satisfied by already resolved application '%s' with version '%s'",
          dependencyVersion, APP_PLATFORM_MINIMAL.getName(), APP_PLATFORM_MINIMAL.getVersion()))))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("dependencyName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(APP_PLATFORM_MINIMAL.getName())))
      .andExpect(jsonPath("$.errors[0].parameters[1].key", is("dependencyVersion")))
      .andExpect(jsonPath("$.errors[0].parameters[1].value", is(dependencyVersion)))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  private static ApplicationDescriptorsValidation validationReq(ApplicationDescriptor... descriptors) {
    return new ApplicationDescriptorsValidation(List.of(descriptors));
  }
}
