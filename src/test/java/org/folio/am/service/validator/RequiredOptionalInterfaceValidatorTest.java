package org.folio.am.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.interfaceDescriptor;
import static org.folio.am.support.TestValues.moduleDescriptor;

import java.util.List;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RequiredOptionalInterfaceValidatorTest {

  private final RequiredOptionalInterfaceValidator validator = new RequiredOptionalInterfaceValidator();

  @Test
  void validate_positive_sameInterfaceInRequiresAndProvides() {
    var descriptor = moduleDescriptor("test-module", "1.0.0")
      .addRequiresItem(new InterfaceReference().id("configuration").version("2.0"))
      .addProvidesItem(interfaceDescriptor("configuration", "2.0"));
    var applicationDescriptor = applicationDescriptor("test-app", "1.0.0").addModuleDescriptorsItem(descriptor);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    validator.validate(validationContext);
  }

  @Test
  void validate_negative_sameInterfaceInRequiresAndOptional() {
    var descriptor = moduleDescriptor("test-module", "1.0.0")
      .addRequiresItem(new InterfaceReference().id("configuration").version("2.0"))
      .addOptionalItem(new InterfaceReference().id("configuration").version("2.0"));
    var applicationDescriptor = applicationDescriptor("test-app", "1.0.0").addModuleDescriptorsItem(descriptor);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Interface cannot be both required and optional")
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("test-module-1.0.0").value("[configuration]")));
      });
  }

  @Test
  void validate_negative_sameInterfaceInUiRequiresAndOptional() {
    var descriptor = new ModuleDescriptor()
      .id("test-ui-1.0.0")
      .addRequiresItem(new InterfaceReference().id("configuration").version("2.0"))
      .addOptionalItem(new InterfaceReference().id("configuration").version("2.0"));
    var applicationDescriptor = applicationDescriptor("test-app", "1.0.0").addUiModuleDescriptorsItem(descriptor);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> validator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Interface cannot be both required and optional")
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("test-ui-1.0.0").value("[configuration]")));
      });
  }
}
