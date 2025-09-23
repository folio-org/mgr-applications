package org.folio.am.controller;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.APPLICATION_NAME;
import static org.folio.am.support.TestConstants.APPLICATION_VERSION;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.applicationReferences;
import static org.folio.am.support.TestValues.validationContext;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.parseResponse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDescriptors;
import org.folio.am.domain.dto.ApplicationDescriptorsValidation;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.service.ApplicationDescriptorsValidationService;
import org.folio.am.service.ApplicationReferencesValidationService;
import org.folio.am.service.ApplicationService;
import org.folio.am.service.ApplicationValidatorService;
import org.folio.am.service.validator.ValidationMode;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.SearchResult;
import org.folio.common.utils.OkapiHeaders;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.security.exception.ForbiddenException;
import org.folio.security.exception.NotAuthorizedException;
import org.folio.security.integration.keycloak.client.KeycloakAuthClient;
import org.folio.spring.cql.CqlQueryValidationException;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.z3950.zing.cql.CQLParseException;

@Log4j2
@UnitTest
@EnableKeycloakSecurity
@WebMvcTest(ApplicationController.class)
@TestPropertySource(properties = "application.router.path-prefix=/")
@Import({ControllerTestConfiguration.class, ApplicationController.class})
class ApplicationControllerTest {

  private static final String MODULE_ID = "mod-test-1.0.0";
  private static final String TOKEN_ISSUER = "https://keycloak/realms/test";
  private static final String TOKEN_SUB = UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @Mock private JsonWebToken jsonWebToken;
  @MockitoBean private KeycloakAuthClient authClient;
  @MockitoBean private JsonWebTokenParser jsonWebTokenParser;
  @MockitoBean private ApplicationValidatorService applicationValidatorService;
  @MockitoBean private ApplicationService applicationService;
  @MockitoBean private ApplicationReferencesValidationService applicationReferencesValidationService;
  @MockitoBean private ApplicationDescriptorsValidationService applicationDescriptorsValidationService;

  @Test
  void get_positive() throws Exception {
    var descriptor = new ApplicationDescriptor().version("1.0.0").name("test-app");
    when(applicationService.get(APPLICATION_ID, false)).thenReturn(descriptor);

    var mvcResult = mockMvc.perform(get("/applications/{id}", APPLICATION_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptor.class);
    assertThat(actual).isEqualTo(descriptor);
  }

  @Test
  void get_negative() throws Exception {
    var errorMessage = "Application not found by id: " + APPLICATION_ID;
    when(applicationService.get(APPLICATION_ID, false)).thenThrow(new EntityNotFoundException(errorMessage));
    mockMvc.perform(get("/applications/{id}", APPLICATION_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  void get_negative_unsupportedError() throws Exception {
    when(applicationService.get(APPLICATION_ID, false)).thenThrow(new UnsupportedOperationException("unsupported"));

    mockMvc.perform(get("/applications/{id}", APPLICATION_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNotImplemented())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("unsupported")))
      .andExpect(jsonPath("$.errors[0].type", is("UnsupportedOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @Test
  void getByQuery_positive() throws Exception {
    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name("test").version("1.0.0");
    var applicationDescriptors = SearchResult.of(singletonList(descriptor));
    when(applicationService.findByQuery("id==" + APPLICATION_ID, 0, 10, false)).thenReturn(applicationDescriptors);
    var mvcResult = mockMvc.perform(get("/applications")
        .param("query", "id==" + APPLICATION_ID)
        .param("offset", String.valueOf(0))
        .param("limit", String.valueOf(10))
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptors.class);
    assertThat(actual).isEqualTo(new ApplicationDescriptors()
      .applicationDescriptors(singletonList(descriptor))
      .totalRecords(1));
  }

  @Test
  void getByQuery_positive_emptyQuery() throws Exception {
    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name("test").version("1.0.0");
    var applicationDescriptors = SearchResult.of(singletonList(descriptor));
    when(applicationService.findByQuery(null, 0, 10, false)).thenReturn(applicationDescriptors);
    var mvcResult = mockMvc.perform(get("/applications")
        .param("offset", String.valueOf(0))
        .param("limit", String.valueOf(10))
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptors.class);
    assertThat(actual).isEqualTo(new ApplicationDescriptors()
      .applicationDescriptors(singletonList(descriptor))
      .totalRecords(1));
  }

  @Test
  void getByQuery_negative_invalidCqlQuery() throws Exception {
    var query = "id==\"" + APPLICATION_ID;
    var expectedErrorMessage = "org.z3950.zing.cql.CQLParseException: expected index or term, got EOF";

    when(applicationService.findByQuery(query, 0, 10, false))
      .thenThrow(new CqlQueryValidationException(new CQLParseException("expected index or term, got EOF", 5)));

    mockMvc.perform(get("/applications")
        .param("query", query)
        .param("offset", String.valueOf(0))
        .param("limit", String.valueOf(10))
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(expectedErrorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("CqlQueryValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void getByQuery_negative_invalidLimit() throws Exception {
    var errorMessage = "getApplicationsByQuery.limit must be greater than or equal to 0";
    mockMvc.perform(get("/applications")
        .param("query", "cql.allRecords=1")
        .param("offset", String.valueOf(0))
        .param("limit", String.valueOf(-100))
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void create_positive() throws Exception {
    var moduleDescriptor = new ModuleDescriptor().id(MODULE_ID).description("test");

    var applicationDescriptor = new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name("test")
      .version("1.0.0")
      .moduleDescriptors(singletonList(moduleDescriptor));

    when(applicationService.create(applicationDescriptor, OKAPI_AUTH_TOKEN, true)).thenReturn(applicationDescriptor);
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var mvcResult = mockMvc.perform(post("/applications")
        .content(asJsonString(applicationDescriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isCreated())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptor.class);
    assertThat(actual).isEqualTo(applicationDescriptor);
  }

  @Test
  void create_positive_validationDisable() throws Exception {
    var applicationDescriptor = applicationDescriptor();

    when(applicationService.create(applicationDescriptor, OKAPI_AUTH_TOKEN, false)).thenReturn(applicationDescriptor);
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var mvcResult = mockMvc.perform(post("/applications")
        .queryParam("check", "false")
        .content(asJsonString(applicationDescriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isCreated())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptor.class);
    assertThat(actual).isEqualTo(applicationDescriptor);
  }

  @Test
  void create_negative() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name("test");
    mockMvc.perform(post("/applications")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("must not be null")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("version")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("null")));
  }

  @Test
  void create_negative_invalidVersion() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var descriptor = new ApplicationDescriptor().name("test").version("1.0");
    mockMvc.perform(post("/applications")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("must match")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("version")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("1.0")));
  }

  @Test
  void create_negative_internalServerError() throws Exception {
    var descriptor = new ApplicationDescriptor().name("test").version("1.0.0");
    when(applicationService.create(descriptor, OKAPI_AUTH_TOKEN, true))
      .thenThrow(new RuntimeException("Unknown error"));
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(post("/applications")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Unknown error")))
      .andExpect(jsonPath("$.errors[0].type", is("RuntimeException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void create_negative_invalidRequestBody() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var errorMsgSubstring = "JSON parse error: Unexpected character ('[' (code 91)): "
      + "was expecting double-quote to start field name";
    mockMvc.perform(post("/applications")
        .content("{[..]")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString(errorMsgSubstring)))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void create_negative_semVersionInvalid() throws Exception {
    var descriptor = applicationDescriptor();
    descriptor.addDependenciesItem(new Dependency().name("app-foo").version("xxx"));

    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(post("/applications")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Invalid semantic version or range(s): \"xxx\"")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("dependencies[0].version")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("xxx")));
  }

  @Test
  void delete_positive() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);
    doNothing().when(applicationService).delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());
  }

  @Test
  void delete_negative_forbidden() throws Exception {
    doNothing().when(applicationService).delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);
    when(authClient.evaluatePermissions(anyMap(), anyString())).thenThrow(new ForbiddenException("test"));
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isForbidden());
  }

  @Test
  void delete_negative_unauthorized() throws Exception {
    doNothing().when(applicationService).delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);
    when(authClient.evaluatePermissions(anyMap(), anyString())).thenThrow(new NotAuthorizedException("test"));

    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void delete_negative_noAuthToken() throws Exception {
    doNothing().when(applicationService).delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void validateApplicationDescriptor_positive() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(post("/applications/validate")
        .content(asJsonString(applicationDescriptor()))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    verify(applicationValidatorService).validate(validationContext());
  }

  @Test
  void validateApplicationDescriptor_positive_withMode() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(post("/applications/validate")
        .content(asJsonString(applicationDescriptor()))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("mode", "basic"))
      .andExpect(status().isNoContent());

    verify(applicationValidatorService).validate(validationContext(), ValidationMode.BASIC);
  }

  @Test
  void validateModulesInterfaceIntegrity_positive() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    mockMvc.perform(post("/applications/validate-interfaces")
        .content(asJsonString(applicationReferences()))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isNoContent());

    verify(applicationReferencesValidationService).validateReferences(applicationReferences());
  }

  @Test
  void validateDescriptorsDependenciesIntegrity_positive() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var requestBody = new ApplicationDescriptorsValidation();
    requestBody.setApplicationDescriptors(List.of(applicationDescriptor()));

    mockMvc.perform(post("/applications/validate-descriptors")
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk());

    verify(applicationDescriptorsValidationService).validateDescriptors(any());
  }

  @Test
  void validateDescriptorsDependenciesIntegrity_negative_emptyDescriptorList() throws Exception {
    when(jsonWebTokenParser.parse(OKAPI_AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn(TOKEN_ISSUER);
    when(jsonWebToken.getSubject()).thenReturn(TOKEN_SUB);

    var requestBody = new ApplicationDescriptorsValidation();
    requestBody.setApplicationDescriptors(emptyList());

    mockMvc.perform(post("/applications/validate-descriptors")
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("size must be between 1 and 2147483647")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("applicationDescriptors")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("[]")));

    verifyNoInteractions(applicationDescriptorsValidationService);
  }

  @Test
  void getByQuery_positive_with_latest_parameter() throws Exception {
    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name(APPLICATION_NAME).version(APPLICATION_VERSION);

    // Mock the service method with new signature (offset, limit first, validation handled internally)
    when(applicationService.filterByAppVersions(APPLICATION_NAME, false, 1, "true",
      "desc", "version"))
      .thenReturn(SearchResult.of(1, singletonList(descriptor)));

    var mvcResult = mockMvc.perform(get("/applications")
        .param("latest", "1")
        .param("appName", APPLICATION_NAME)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptors.class);
    assertThat(actual).isEqualTo(new ApplicationDescriptors()
      .applicationDescriptors(singletonList(descriptor))
      .totalRecords(1));
  }

  @Test
  void getByQuery_positive_with_both_latest_and_preRelease() throws Exception {
    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name(APPLICATION_NAME).version(APPLICATION_VERSION);

    // Mock the service method with new signature
    when(applicationService.filterByAppVersions(APPLICATION_NAME, false, 2, "false",
      "desc", "version"))
      .thenReturn(SearchResult.of(1, singletonList(descriptor)));

    var mvcResult = mockMvc.perform(get("/applications")
        .param("latest", "2")
        .param("preRelease", "false")
        .param("appName", APPLICATION_NAME)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptors.class);
    assertThat(actual).isEqualTo(new ApplicationDescriptors()
      .applicationDescriptors(singletonList(descriptor))
      .totalRecords(1));
  }

  @Test
  void getByQuery_positive_with_orderBy_parameter() throws Exception {
    var descriptor = new ApplicationDescriptor().id(APPLICATION_ID).name(APPLICATION_NAME).version(APPLICATION_VERSION);

    when(applicationService.filterByAppVersions(APPLICATION_NAME, false, null, "true",
      "desc", "name"))
      .thenReturn(SearchResult.of(1, singletonList(descriptor)));

    var mvcResult = mockMvc.perform(get("/applications")
        .param("orderBy", "name")
        .param("order", "desc")
        .param("appName", APPLICATION_NAME)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, ApplicationDescriptors.class);
    assertThat(actual).isEqualTo(new ApplicationDescriptors()
      .applicationDescriptors(singletonList(descriptor))
      .totalRecords(1));
  }

  @Test
  void getByQuery_negative_missing_filter_with_advanced_params() throws Exception {
    // Mock service to throw validation exception
    when(applicationService.filterByAppVersions(null, false, 1, "true",
      "desc", "version"))
      .thenThrow(new IllegalArgumentException("Filter parameter `appName` is required when using "
        + "`latest`, `preRelease`, `order`, `orderBy` for version-specific filtering"));

    mockMvc.perform(get("/applications")
        .param("latest", "1")
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }
}
