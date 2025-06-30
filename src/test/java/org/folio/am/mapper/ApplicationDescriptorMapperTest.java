package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.OPTIONAL;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.PROVIDES;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.REQUIRES;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.common.utils.CollectionUtils.takeOne;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.dto.Module;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDescriptorMapperTest {

  private final MappingMethods mappingMethods = new MappingMethods();
  private final ApplicationDescriptorMapper mapper =
    new org.folio.am.mapper.ApplicationDescriptorMapperImpl(mappingMethods);

  @Test
  void convert_applicationDescriptor_to_applicationDescriptorEntity_success() {
    var dependency1 = new Dependency().name("dep1").version("0.0.1");
    var dependency2 = new Dependency().name("dep2").version("0.0.2").optional(true);

    var descriptor = new ApplicationDescriptor()
      .id(APPLICATION_ID).name("app1").version("1.0.0")
      .modules(List.of(new Module().id("module1-1.0.0").name("module1").version("1.0.0")))
      .uiModules(List.of(new Module().id("uiModule1-1.0.0").name("uiModule1").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor().id("module1-1.0.0").description("module1")))
      .uiModuleDescriptors(List.of(new ModuleDescriptor().id("uiModule1-1.0.0").description("uiModule1")))
      .dependencies(List.of(dependency1, dependency2));

    var applicationDescriptorEntity = mapper.convert(descriptor);

    assertThat(applicationDescriptorEntity.getId()).isEqualTo(APPLICATION_ID);
    assertThat(applicationDescriptorEntity.getName()).isEqualTo("app1");
    assertThat(applicationDescriptorEntity.getVersion()).isEqualTo("1.0.0");
    assertThat(takeOne(applicationDescriptorEntity.getModules()).getId()).isEqualTo("module1-1.0.0");
    assertThat(takeOne(applicationDescriptorEntity.getUiModules()).getId()).isEqualTo("uiModule1-1.0.0");

    var deps = applicationDescriptorEntity.getApplicationDescriptor().getDependencies();
    assertThat(deps).hasSize(2);
    var dep1 = deps.stream().filter(dep -> "dep1".equals(dep.getName())).findFirst().orElseThrow();
    var dep2 = deps.stream().filter(dep -> "dep2".equals(dep.getName())).findFirst().orElseThrow();
    assertEquals(false, dep1.getOptional());
    assertEquals(true, dep2.getOptional());
  }

  @Test
  void convert_module_to_moduleEntity_success() {
    var moduleId = "test-1.0.0";
    var moduleName = "test";
    var providedInterface = "interface";
    var providedInterfaceVersion = "1.2";
    var requiredInterface = "req-interface";
    var requiredInterfaceVersion = "1.0";
    var optionalInterface = "opt-interface";
    var optionalInterfaceVersion = "3.4";

    var module = new Module().id(moduleId).name(moduleName);
    var moduleDescriptor = new ModuleDescriptor().id(moduleId).description(moduleName)
      .addProvidesItem(new InterfaceDescriptor().id(providedInterface).version(providedInterfaceVersion))
      .addRequiresItem(new InterfaceReference().id(requiredInterface).version(requiredInterfaceVersion))
      .addOptionalItem(new InterfaceReference().id(optionalInterface).version(optionalInterfaceVersion));

    var appDescriptor = new ApplicationDescriptor()
      .addModulesItem(module)
      .addModuleDescriptorsItem(moduleDescriptor);

    var moduleEntities = mapper.convert(appDescriptor).getModules();

    assertEquals(1, moduleEntities.size());
    var moduleEntity = takeOne(moduleEntities);

    assertThat(moduleEntity.getInterfaces()).hasSize(3)
      .containsExactlyInAnyOrder(
        TestValues.interfaceReferenceEntity(moduleId, providedInterface, providedInterfaceVersion, PROVIDES),
        TestValues.interfaceReferenceEntity(moduleId, requiredInterface, requiredInterfaceVersion, REQUIRES),
        TestValues.interfaceReferenceEntity(moduleId, optionalInterface, optionalInterfaceVersion, OPTIONAL));
  }
}
