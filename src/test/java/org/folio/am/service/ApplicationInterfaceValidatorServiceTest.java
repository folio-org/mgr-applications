package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationInterfaceValidatorServiceTest {

  @Mock private ApplicationService applicationService;
  @InjectMocks private ApplicationInterfaceValidatorService applicationInterfaceValidatorService;

  @Test
  void validate_positive() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");
    applicationEntity1.setId("app1-1.0.0");
    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0")));
    var beModule = new ModuleEntity();
    beModule.setDescriptor(beModuleDescriptor);
    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0.0")));
    var uiModule = new UiModuleEntity();
    uiModule.setDescriptor(uiModuleDescriptor);
    applicationEntity1.setModules(Set.of(beModule));
    applicationEntity1.setUiModules(List.of(uiModule));
    var applicationDescriptorDependency = new Dependency().name("app2").version("^2.0.1");
    var applicationDescriptor1 = new ApplicationDescriptor().dependencies(List.of(applicationDescriptorDependency));
    applicationEntity1.setApplicationDescriptor(applicationDescriptor1);

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setName("app2");
    applicationEntity2.setVersion("2.0.1");
    applicationEntity2.setId("app2-2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("configuration").version("1.0.0")));
    var beModule2 = new ModuleEntity();
    beModule2.setDescriptor(beModuleDescriptor2);
    var uiModule2 = new UiModuleEntity();
    var uiModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("ui-settings").version("1.0.0")));
    uiModule2.setDescriptor(uiModuleDescriptor2);
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    applicationEntity2.setModules(Set.of(beModule2));
    applicationEntity1.setUiModules(List.of(uiModule2));

    var apps = List.of("app1-1.0.0", "app2-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatNoException().isThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences));
  }

  @Test
  void validate_negative_applicationIdNotExist() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setId("app1-1.0.0");
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");
    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setId("app1-2.0.1");
    applicationEntity2.setName("app1");
    applicationEntity2.setVersion("2.0.1");
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    var apps = List.of("app1-1.0.0", "app1-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1));

    assertThatThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Applications not exist by ids : app1-2.0.1");
  }

  @Test
  void validate_negative_providedSameApplicationWithDifferentVersion() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setId("app1-1.0.0");
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setId("app1-2.0.1");
    applicationEntity2.setName("app1");
    applicationEntity2.setVersion("2.0.1");
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    var apps = List.of("app1-1.0.0", "app1-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Provided same applications with different versions")
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("applicationNames").value("app1")));
      });
  }

  @Test
  void validate_negative_applicationDependencyByNameIsMissed() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setId("app1-1.0.0");
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");
    var dependency = new Dependency().name("app3").version("1.0.0");
    var applicationDescriptor1 = new ApplicationDescriptor().dependencies(List.of(dependency));
    applicationEntity1.setApplicationDescriptor(applicationDescriptor1);

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setId("app2-3.0.1");
    applicationEntity2.setName("app2");
    applicationEntity2.setVersion("3.0.1");
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    var apps = List.of("app1-1.0.0", "app2-3.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application dependency by name app3 not exist");
  }

  @Test
  void validate_negative_applicationDependencyByVersionIsMissed() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setId("app1-1.0.0");
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    var applicationDescriptor1 = new ApplicationDescriptor().dependencies(List.of(dependency));
    applicationEntity1.setApplicationDescriptor(applicationDescriptor1);

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setId("app2-3.0.1");
    applicationEntity2.setName("app2");
    applicationEntity2.setVersion("3.0.1");
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());

    var apps = List.of("app1-1.0.0", "app2-3.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application dependency by name app2 and version ^2.0.1 not exist");
  }

  @Test
  void validate_negative_interfaceIsMissed() {
    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setName("app1");
    applicationEntity1.setVersion("1.0.0");
    applicationEntity1.setId("app1-1.0.0");
    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0")));
    var beModule = new ModuleEntity();
    beModule.setDescriptor(beModuleDescriptor);
    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0.0")));
    var uiModule = new UiModuleEntity();
    uiModule.setDescriptor(uiModuleDescriptor);
    applicationEntity1.setModules(Set.of(beModule));
    applicationEntity1.setUiModules(List.of(uiModule));
    var applicationDescriptorDependency = new Dependency().name("app2").version("^2.0.1");
    var applicationDescriptor1 = new ApplicationDescriptor().dependencies(List.of(applicationDescriptorDependency));
    applicationEntity1.setApplicationDescriptor(applicationDescriptor1);

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setName("app2");
    applicationEntity2.setVersion("2.0.1");
    applicationEntity2.setId("app2-2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0")));
    var beModule2 = new ModuleEntity();
    beModule2.setDescriptor(beModuleDescriptor2);
    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    applicationEntity2.setModules(Set.of(beModule2));

    var apps = List.of("app1-1.0.0", "app2-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatThrownBy(() -> applicationInterfaceValidatorService.validate(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Missing dependencies found for the applications")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .hasSize(2)
          .containsExactly(new Parameter().key("app1-1.0.0").value("configuration 1.0.0;ui-settings 1.0.0"),
            new Parameter().key("app2-2.0.1").value("configuration 1.0.0")));
  }
}
