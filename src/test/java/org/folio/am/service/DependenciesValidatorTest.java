package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class DependenciesValidatorTest {

  private final DependenciesValidator dependenciesValidator = new DependenciesValidator();

  @Test
  void validateDependencies_negative_providedSameApplicationWithDifferentVersion() {
    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setId("app1-1.0.0");
    applicationDescriptor.setName("app1");
    applicationDescriptor.setVersion("1.0.0");

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setId("app1-2.0.1");
    applicationDescriptor2.setName("app1");
    applicationDescriptor2.setVersion("2.0.1");

    var applicationDescriptors = List.of(applicationDescriptor, applicationDescriptor2);

    assertThatThrownBy(() -> dependenciesValidator.validateDependencies(applicationDescriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Used same applications with different versions")
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("applicationNames").value("app1")));
      });
  }

  @Test
  void validateDependencies_negative_applicationDependencyByNameIsMissed() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setId("app1-1.0.0");
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    var dependency = new Dependency().name("app3").version("1.0.0");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setId("app2-3.0.1");
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("3.0.1");

    var applicationDescriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    assertThatThrownBy(() -> dependenciesValidator.validateDependencies(applicationDescriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application dependency by name app3 not exist");
  }

  @Test
  void validateDependencies_negative_applicationDependencyByVersionIsMissed() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setId("app1-1.0.0");
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setId("app2-3.0.1");
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("3.0.1");

    var applicationDescriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    assertThatThrownBy(() -> dependenciesValidator.validateDependencies(applicationDescriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application dependency by name app2 and version ^2.0.1 not exist");
  }

  @Test
  void validateInterfaces_negative_interfaceIsMissed() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0")));
    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0.0")));
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setModuleDescriptors(List.of(beModuleDescriptor, uiModuleDescriptor));
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.1");
    applicationDescriptor2.setId("app2-2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0")));
    applicationDescriptor2.setModuleDescriptors(List.of(beModuleDescriptor2));

    var applicationDescriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    assertThatThrownBy(() -> dependenciesValidator.validateInterfaces(applicationDescriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Missing interfaces found for the applications")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .hasSize(2)
          .containsExactly(new Parameter().key("app1-1.0.0").value("configuration 1.0.0;ui-settings 1.0.0"),
            new Parameter().key("app2-2.0.1").value("configuration 1.0.0")));
  }

  @Test
  void validateDependencies_positive_withOptionalDependency() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setId("app1-1.0.0");
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");

    // Create a dependency marked as optional
    var optionalDependency = new Dependency().name("app3").version("1.0.0").optional(true);
    var requiredDependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(optionalDependency, requiredDependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setId("app2-2.0.1");
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.1");

    var applicationDescriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    // Should not throw exception because app3 dependency is optional
    assertThatNoException().isThrownBy(() -> dependenciesValidator.validateDependencies(applicationDescriptors));
  }

  @Test
  void validateDependencies_negative_withMixedDependencies() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setId("app1-1.0.0");
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");

    // Create mixed dependencies - one optional that's missing and one required that's missing
    var optionalDependency = new Dependency().name("app3").version("1.0.0").optional(true);
    var requiredDependency = new Dependency().name("app4").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(optionalDependency, requiredDependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setId("app2-2.0.1");
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.1");

    var applicationDescriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    // Should throw exception because app4 dependency is required but missing
    assertThatThrownBy(() -> dependenciesValidator.validateDependencies(applicationDescriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application dependency by name app4 not exist");
  }
}
