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
import org.folio.common.domain.model.ModuleDescriptor;
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
class ModuleDescriptorValidatorTest {

  @InjectMocks private ModuleDescriptorValidator validator;
  @Mock private ApplicationDescriptor applicationDescriptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationDescriptor);
  }

  @Test
  void validate_positive() {
    var module = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);
    var moduleDescriptor = new ModuleDescriptor().id(SERVICE_ID);
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getModules()).thenReturn(List.of(module));
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(List.of(moduleDescriptor));

    validator.validate(context);

    verify(applicationDescriptor).getModules();
    verify(applicationDescriptor).getModuleDescriptors();
  }

  @Test
  void validate_positive_emptyModuleAndModuleDescriptors() {
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getModules()).thenReturn(emptyList());
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(emptyList());

    validator.validate(context);

    verify(applicationDescriptor).getModules();
    verify(applicationDescriptor).getModuleDescriptors();
  }

  @Test
  void validate_positive_nullModuleAndModuleDescriptors() {
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getModules()).thenReturn(null);
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(null);

    validator.validate(context);

    verify(applicationDescriptor).getModules();
    verify(applicationDescriptor).getModuleDescriptors();
  }

  @Test
  void validate_negative_emptyModuleDescriptors() {
    var module = new Module().name(SERVICE_NAME).version(SERVICE_VERSION);
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getModules()).thenReturn(List.of(module));
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(emptyList());

    assertThatThrownBy(() -> validator.validate(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Module descriptors are not found in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("moduleDescriptors").value("[" + SERVICE_ID + "]")));
      });
  }

  @Test
  void validate_negative_emptyModules() {
    var moduleDescriptor = new ModuleDescriptor().id(SERVICE_ID);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getModules()).thenReturn(emptyList());
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(List.of(moduleDescriptor));

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Module descriptors are not used in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("moduleDescriptors").value("[" + SERVICE_ID + "]")));
      });
  }

  @Test
  void validate_negative_missingModuleDefinition() {
    var moduleDescriptor1 = new ModuleDescriptor().id("mod-foo-1.0.0");
    var moduleDescriptor2 = new ModuleDescriptor().id("mod-bar-1.0.0");
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getModules()).thenReturn(List.of(TestValues.module("mod-foo", "1.0.0")));
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(List.of(moduleDescriptor1, moduleDescriptor2));

    assertThatThrownBy(() -> validator.validate(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Module descriptors are not used in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("moduleDescriptors").value("[mod-bar-1.0.0]")));
      });
  }

  @Test
  void validate_negative_missingModuleDescriptor() {
    var moduleDescriptor1 = new ModuleDescriptor().id("mod-foo-1.0.0");
    var context = TestValues.validationContext(applicationDescriptor);

    when(applicationDescriptor.getArtifactId()).thenReturn(APPLICATION_ID);
    when(applicationDescriptor.getModules()).thenReturn(
      List.of(TestValues.module("mod-foo", "1.0.0"), TestValues.module("mod-bar", "1.0.0")));
    when(applicationDescriptor.getModuleDescriptors()).thenReturn(List.of(moduleDescriptor1));

    assertThatThrownBy(() -> validator.validate(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Module descriptors are not found in application descriptor: %s", APPLICATION_ID)
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("moduleDescriptors").value("[mod-bar-1.0.0]")));
      });
  }
}
