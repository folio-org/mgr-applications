package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.ModuleType;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ApplicationEntityMapperTest {

  private final ApplicationEntityMapper mapper = new org.folio.am.mapper.ApplicationEntityMapperImpl();

  @Test
  void convert() {
    var applicationEntity = createApplication("app1-1.0.0", "app1", "1.0.0");

    var beModuleDescriptor = new ModuleDescriptor();
    beModuleDescriptor.setId("be-module-descriptor-id");
    var beModule = createModule(ModuleType.BACKEND, beModuleDescriptor);

    var uiModuleDescriptor = new ModuleDescriptor();
    uiModuleDescriptor.setId("ui-module-descriptor-id");
    var uiModule = createModule(ModuleType.UI, uiModuleDescriptor);

    applicationEntity.setModules(Set.of(beModule, uiModule));

    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setName("app1");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor.setDependencies(List.of(dependency));
    applicationEntity.setApplicationDescriptor(applicationDescriptor);

    var actualApplicationDescriptor = mapper.convert(applicationEntity);

    assertThat(actualApplicationDescriptor.getName()).isEqualTo(applicationEntity.getName());
    assertThat(actualApplicationDescriptor.getVersion()).isEqualTo(applicationEntity.getVersion());
    assertThat(actualApplicationDescriptor.getId()).isEqualTo(applicationEntity.getId());
    assertThat(actualApplicationDescriptor.getDependencies()).containsOnly(dependency);
    assertThat(actualApplicationDescriptor.getModuleDescriptors()).contains(beModuleDescriptor);
    assertThat(actualApplicationDescriptor.getUiModuleDescriptors()).contains(uiModuleDescriptor);
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
