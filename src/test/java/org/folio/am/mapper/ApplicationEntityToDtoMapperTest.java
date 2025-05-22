package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ApplicationEntityToDtoMapperTest {

  private final ApplicationEntityToDtoMapper mapper =
    new org.folio.am.mapper.ApplicationEntityToDtoMapperImpl();

  @Test
  void convert() {
    var applicationEntity = new ApplicationEntity();
    applicationEntity.setName("app1");
    applicationEntity.setVersion("1.0.0");
    applicationEntity.setId("app1-1.0.0");
    var beModuleDescriptor = new ModuleDescriptor();
    beModuleDescriptor.setId("be-module-descriptor-id");
    var beModule = new ModuleEntity();
    beModule.setDescriptor(beModuleDescriptor);
    var uiModuleDescriptor = new ModuleDescriptor();
    uiModuleDescriptor.setId("ui-module-descriptor-id");
    var uiModule = new UiModuleEntity();
    uiModule.setDescriptor(uiModuleDescriptor);
    applicationEntity.setModules(Set.of(beModule));
    applicationEntity.setUiModules(List.of(uiModule));

    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setName("app1");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor.setDependencies(List.of(dependency));
    applicationEntity.setApplicationDescriptor(applicationDescriptor);

    var applicationDto = mapper.convert(applicationEntity);

    assertThat(applicationDto.getName()).isEqualTo(applicationEntity.getName());
    assertThat(applicationDto.getVersion()).isEqualTo(applicationEntity.getVersion());
    assertThat(applicationDto.getId()).isEqualTo(applicationEntity.getId());
    assertThat(applicationDto.getDependencies()).containsOnly(dependency);
    assertThat(applicationDto.getModuleDescriptors()).contains(beModuleDescriptor, uiModuleDescriptor);
  }
}
