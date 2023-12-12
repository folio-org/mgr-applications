package org.folio.am.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.module;
import static org.folio.am.support.TestValues.moduleDescriptor;
import static org.folio.am.support.TestValues.validationContext;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleDescriptorsSourceValidatorTest {

  @InjectMocks private ModuleDescriptorsSourceValidator moduleDescriptorsSourceValidator;

  @Test
  void validate_positive() {
    var module1 = module("foo-module", "1.0.0");
    var module2 = module("bar-module", "1.0.0", "http://bar-module.com/module/1.0.0");
    var moduleDescriptor1 = moduleDescriptor("foo-module", "1.0.0");
    var moduleDescriptor2 = moduleDescriptor("bar-module", "1.0.0");
    var uiModuleDescriptor = moduleDescriptor("ui-module", "1.0.0");
    var uiModule = module("ui-module", "1.0.0", "http://ui-module.com/module/1.0.0");

    var applicationDescriptor =
      applicationDescriptor("app-test", "1.0.0").addModulesItem(module1).addModulesItem(module2)
        .addUiModulesItem(uiModule)
        .addModuleDescriptorsItem(moduleDescriptor1);
    var context = validationContext(applicationDescriptor, List.of(moduleDescriptor2), List.of(uiModuleDescriptor),
      List.of());

    moduleDescriptorsSourceValidator.validate(context);
  }

  @Test
  void validate_negative_urlAndDescriptorExist() {
    var module = module("bar-module", "1.0.0", "http://bar-module.com/module/1.0.0");
    var moduleDescriptor = moduleDescriptor("bar-module", "1.0.0");

    var applicationDescriptor =
      applicationDescriptor("app-test", "1.0.0").addModulesItem(module)
        .addModuleDescriptorsItem(moduleDescriptor);
    var context = validationContext(applicationDescriptor, List.of(moduleDescriptor), List.of(), List.of());

    var exception = assertThrows(RequestValidationException.class,
      () -> moduleDescriptorsSourceValidator.validate(context));
    assertThat(exception.getMessage())
      .isEqualTo("Validation failed. Modules are not valid by URL and module descriptors.");
    assertThat(exception.getErrorParameters().get(0))
      .isEqualTo(new Parameter().key("modules").value("[bar-module-1.0.0]"));
  }

  @Test
  void validate_negative_uiUrlAndDescriptorExist() {
    var uiModuleDescriptor = moduleDescriptor("ui-module", "1.0.0");
    var uiModule = module("ui-module", "1.0.0", "http://ui-module.com/module/1.0.0");

    var applicationDescriptor =
      applicationDescriptor("app-test", "1.0.0").addUiModuleDescriptorsItem(uiModuleDescriptor)
        .addUiModulesItem(uiModule);
    var context = validationContext(applicationDescriptor, List.of(), List.of(uiModuleDescriptor), List.of());

    var exception =
      assertThrows(RequestValidationException.class, () -> moduleDescriptorsSourceValidator.validate(context));
    assertThat(exception.getMessage())
      .isEqualTo("Validation failed. Modules are not valid by URL and module descriptors.");
    assertThat(exception.getErrorParameters().get(0))
      .isEqualTo(new Parameter().key("uiModules").value("[ui-module-1.0.0]"));
  }
}
