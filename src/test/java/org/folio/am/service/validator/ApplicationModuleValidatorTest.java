package org.folio.am.service.validator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.APPLICATION_NAME;
import static org.folio.am.support.TestConstants.MODULE_FOO_NAME;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.validationContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.service.ApplicationService;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationModuleValidatorTest {

  @InjectMocks private ApplicationModuleValidator validator;
  @Mock private ApplicationService applicationService;
  @Mock private ApplicationDescriptor applicationDescriptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationService, applicationDescriptor);
  }

  @Test
  void create_positive_clean() {
    var module = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);

    when(applicationDescriptor.getModules()).thenReturn(List.of(module));
    when(applicationService.findApplicationsByModuleIds(List.of(SERVICE_ID))).thenReturn(emptyList());
    var validationContext = ValidationContext.builder().applicationDescriptor(applicationDescriptor).build();

    validator.validate(validationContext);

    verify(applicationDescriptor).getModules();
  }

  @Test
  void create_positive_moduleBelongsToSameApplicationName() {
    var module1 = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);
    var module2 = new Module().name("test").version(SERVICE_VERSION);
    var otherDesc = applicationDescriptor(APPLICATION_NAME, "2.0.0").modules(List.of(module1, module2));

    when(applicationDescriptor.getName()).thenReturn(APPLICATION_NAME);
    when(applicationDescriptor.getModules()).thenReturn(List.of(module1));
    when(applicationService.findApplicationsByModuleIds(List.of(SERVICE_ID))).thenReturn(List.of(otherDesc));
    var validationContext = validationContext(applicationDescriptor);

    validator.validate(validationContext);

    verify(applicationDescriptor).getModules();
  }

  @Test
  void create_negative_sameModulesBelongToDifferentApp() {
    var module1 = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);
    var module2 = new Module().name(MODULE_FOO_NAME).version(SERVICE_VERSION);
    var persistedApplication1 = applicationDescriptor(APPLICATION_NAME, "2.0.0").modules(List.of(module1, module2));
    var persistedApplication2 = applicationDescriptor("another-app-name", "6.6.6").modules(List.of(module1, module2));

    when(applicationDescriptor.getName()).thenReturn("another-app-name");
    when(applicationDescriptor.getModules()).thenReturn(List.of(module1));
    when(applicationService.findApplicationsByModuleIds(List.of(SERVICE_ID))).thenReturn(
      List.of(persistedApplication1, persistedApplication2));
    var validationContext = validationContext(applicationDescriptor);

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Modules belong to other Applications")
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(
          List.of(new Parameter().key(APPLICATION_NAME).value(List.of(SERVICE_ID).toString())));
      });

    verify(applicationDescriptor).getModules();
  }
}
