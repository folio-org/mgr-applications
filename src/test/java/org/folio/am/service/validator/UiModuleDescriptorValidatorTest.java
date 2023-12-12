package org.folio.am.service.validator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.error.Parameter;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UiModuleDescriptorValidatorTest {

  @InjectMocks private UiModuleDescriptorValidator validator;
  @Mock private ApplicationDescriptor applicationDescriptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationDescriptor);
  }

  @Test
  void validate_positive() {
    var module = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);
    var moduleDescriptor = new ModuleDescriptor().id(SERVICE_ID);
    when(applicationDescriptor.getUiModules()).thenReturn(List.of(module));
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(List.of(moduleDescriptor));
    var validationContext = TestValues.validationContext(applicationDescriptor);

    validator.validate(validationContext);

    verify(applicationDescriptor).getUiModules();
    verify(applicationDescriptor).getUiModuleDescriptors();
  }

  @Test
  void validate_positive_emptyModuleAndModuleDescriptors() {
    var validationContext = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getUiModules()).thenReturn(emptyList());
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(emptyList());

    validator.validate(validationContext);

    verify(applicationDescriptor).getUiModules();
    verify(applicationDescriptor).getUiModuleDescriptors();
  }

  @Test
  void validate_positive_nullUiModuleAndModuleDescriptors() {
    var validationContext = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getUiModules()).thenReturn(null);
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(null);

    validator.validate(validationContext);

    verify(applicationDescriptor).getUiModules();
    verify(applicationDescriptor).getUiModuleDescriptors();
  }

  @Test
  void validate_negative_uiModuleDescriptorsEmpty() {
    var module = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getUiModules()).thenReturn(List.of(module));
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(emptyList());
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("UI Module descriptors are not found in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("uiModuleDescriptors").value("[" + SERVICE_ID + "]")));
      });
  }

  @Test
  void validate_negative_uiModulesEmpty() {
    var moduleDescriptor = new ModuleDescriptor().id(SERVICE_ID);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getUiModules()).thenReturn(emptyList());
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(List.of(moduleDescriptor));
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("UI Module descriptors are not used in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("uiModuleDescriptors").value("[" + SERVICE_ID + "]")));
      });
  }

  @Test
  void validate_negative_missingUiModuleDefinition() {
    var moduleDescriptor1 = new ModuleDescriptor().id("ui-foo-1.0.0");
    var moduleDescriptor2 = new ModuleDescriptor().id("ui-bar-1.0.0");
    var validationContext = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getUiModules()).thenReturn(List.of(TestValues.module("ui-foo", "1.0.0")));
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(List.of(moduleDescriptor1, moduleDescriptor2));

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("UI Module descriptors are not used in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("uiModuleDescriptors").value("[ui-bar-1.0.0]")));
      });
  }

  @Test
  void validate_negative_missingUiModuleDescriptor() {
    var moduleDescriptor1 = new ModuleDescriptor().id("ui-foo-1.0.0");
    var uiModules = List.of(TestValues.module("ui-foo", "1.0.0"), TestValues.module("ui-bar", "1.0.0"));
    var validationContext = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getUiModules()).thenReturn(uiModules);
    when(applicationDescriptor.getUiModuleDescriptors()).thenReturn(List.of(moduleDescriptor1));

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("UI Module descriptors are not found in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("uiModuleDescriptors").value("[ui-bar-1.0.0]")));
      });
  }
}
