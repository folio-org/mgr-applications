package org.folio.am.service.validator;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.interfaceDescriptor;
import static org.folio.am.support.TestValues.moduleDescriptor;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class InterfaceNameValidatorTest {

  private final InterfaceNameValidator interfaceNameValidator = new InterfaceNameValidator();

  @MethodSource("validModuleNames")
  @ParameterizedTest(name = "[{index}] {0} {1}")
  void validate_positive(String id, String version) {
    var providedInterface = interfaceDescriptor(id, version);
    var descriptor = moduleDescriptor("test", "version").addProvidesItem(providedInterface);
    var applicationDescriptor = applicationDescriptor("test", "version").addModuleDescriptorsItem(descriptor);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    interfaceNameValidator.validate(validationContext);
  }

  @MethodSource("notValidModulesNames")
  @ParameterizedTest(name = "[{index}] {0} {1}")
  void validate_negative(String id, String version) {
    var providedInterfaces = interfaceDescriptor(id, version);
    var descriptor = moduleDescriptor("test", "version").addProvidesItem(providedInterfaces);
    var applicationDescriptor = applicationDescriptor("test", "version").addModuleDescriptorsItem(descriptor);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> interfaceNameValidator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("Invalid provided interface name or version")
      .satisfies(error -> {
        var descriptorId = "test-version";
        var parameters = ((RequestValidationException) error).getErrorParameters();
        var parameter =
          new Parameter().key(descriptorId).value(format("[%1$s %2$s]", id, version));
        assertThat(parameters).contains(parameter);
      });
  }

  private static Stream<Arguments> validModuleNames() {
    return Stream.of(
      arguments("test", "1.0"),
      arguments("test.1", "10.10"),
      arguments("test1.-1", "1111.0"),
      arguments("_testTEST", "1.4"),
      arguments(".-1test_", "1.11"),
      arguments("_tenant2.0", "3.8"),
      arguments("_tenant", "2.9"),
      arguments("RCP0_rcp", "5.6")
    );
  }

  private static Stream<Arguments> notValidModulesNames() {
    return Stream.of(
      arguments("valid-id", "1.2.3.4 1.0"),
      arguments("notvalid_id", "1.2.03"),
      arguments("notvalid/id", "12.3"),
      arguments("notvalid_id123", "1.2.3-alpha.01"),
      arguments("not=valid-id", "1.3"),
      arguments("valid-id", "::0.2.0"),
      arguments("valid-id123", "1..2"),
      arguments("notvalid#id", "1.3"),
      arguments("not valid id", "1.2.3-alpha"),
      arguments("valid-id", "1.2."),
      arguments("notvalid_id-123", "1..3"),
      arguments("valid-id", "1.02.3"),
      arguments("notvalid(id)", "0.0"),
      arguments("valid-id", "+1.2.3")
    );
  }
}
