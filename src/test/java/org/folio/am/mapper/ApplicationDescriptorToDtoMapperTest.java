package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ApplicationDescriptorToDtoMapperTest {

  private final ApplicationDescriptorToDtoMapper mapper =
    new org.folio.am.mapper.ApplicationDescriptorToDtoMapperImpl();

  @Test
  void convert_positive() {
    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setName("app1");
    applicationDescriptor.setVersion("1.0.0");
    applicationDescriptor.setId("app1-1.0.0");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor.setDependencies(List.of(dependency));
    var beModuleDescriptor =  new ModuleDescriptor();
    beModuleDescriptor.setId("be-module-descriptor-id");
    applicationDescriptor.setModuleDescriptors(List.of(beModuleDescriptor));
    var uiModuleDescriptor = new ModuleDescriptor();
    uiModuleDescriptor.setId("ui-module-descriptor-id");
    applicationDescriptor.setUiModuleDescriptors(List.of(uiModuleDescriptor));

    var applicationDto = mapper.convert(applicationDescriptor);

    assertThat(applicationDto.getName()).isEqualTo(applicationDescriptor.getName());
    assertThat(applicationDto.getVersion()).isEqualTo(applicationDescriptor.getVersion());
    assertThat(applicationDto.getId()).isEqualTo(applicationDescriptor.getId());
    assertThat(applicationDto.getDependencies()).containsOnly(dependency);
    assertThat(applicationDto.getModuleDescriptors()).contains(beModuleDescriptor, uiModuleDescriptor);
  }
}
