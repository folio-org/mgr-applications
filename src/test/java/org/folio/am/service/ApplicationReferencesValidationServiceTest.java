package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationReferencesValidationServiceTest {

  @Mock private ApplicationService applicationService;
  @Spy private DependenciesValidator dependenciesValidator;
  @InjectMocks private ApplicationReferencesValidationService applicationReferencesValidationService;

  @Test
  void validate_positive() {
    var applicationEntity1 = createApplication("app1-1.0.0", "app1", "1.0.0");

    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0 2.0.0")));
    var beModule = createModule(ModuleType.BACKEND, beModuleDescriptor);

    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0.0")));
    var uiModule = createModule(ModuleType.UI, uiModuleDescriptor);

    applicationEntity1.setModules(Set.of(beModule, uiModule));

    var applicationDescriptorDependency = new Dependency().name("app2").version("^2.0.1");
    var applicationDescriptor1 = new ApplicationDescriptor().dependencies(List.of(applicationDescriptorDependency));
    applicationEntity1.setApplicationDescriptor(applicationDescriptor1);

    var applicationEntity2 = createApplication("app2-2.0.1", "app2", "2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("configuration").version("1.0.0")));
    var beModule2 = createModule(ModuleType.BACKEND, beModuleDescriptor2);

    var uiModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("ui-settings").version("1.0.0")));
    var uiModule2 = createModule(ModuleType.UI, uiModuleDescriptor2);

    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    applicationEntity2.setModules(Set.of(beModule2, uiModule2));

    var apps = List.of("app1-1.0.0", "app2-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    assertThatNoException().isThrownBy(() -> applicationReferencesValidationService
      .validateReferences(applicationReferences));
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_negative_applicationIdNotExist() {
    var applicationEntity1 = createApplication("app1-1.0.0", "app1", "1.0.0");

    var applicationEntity2 = createApplication("app1-2.0.1", "app1", "2.0.1");

    applicationEntity2.setApplicationDescriptor(new ApplicationDescriptor());
    var apps = List.of("app1-1.0.0", "app1-2.0.1");
    var applicationReferences = new ApplicationReferences().applicationIds(new LinkedHashSet<>(apps));

    when(applicationService.findByIdsWithModules(apps)).thenReturn(List.of(applicationEntity1));

    assertThatThrownBy(() -> applicationReferencesValidationService.validateReferences(applicationReferences))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Applications not exist: ids = app1-2.0.1");
  }

  private static ApplicationEntity createApplication(String id, String name, String version) {
    var applicationEntity = new ApplicationEntity();
    applicationEntity.setName(name);
    applicationEntity.setVersion(version);
    applicationEntity.setId(id);
    return applicationEntity;
  }

  private static ModuleEntity createModule(ModuleType backend, ModuleDescriptor beModuleDescriptor) {
    var moduleEntity = new ModuleEntity();
    moduleEntity.setType(backend);
    moduleEntity.setDescriptor(beModuleDescriptor);
    return moduleEntity;
  }
}
