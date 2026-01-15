package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.ModuleType;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class MappingMethodsTest {

  private static final String BACKEND_MODULE_ID = "mod-be-1.0.0";
  private static final String BACKEND_MODULE_NAME = "mod-be";
  private static final String MODULE_VERSION = "1.0.0";

  private static final String UI_MODULE_ID = "mod-ui-1.0.0";
  private static final String UI_MODULE_NAME = "mod-ui";

  private static final String INTERFACE_ID = "interface-1";
  private static final String INTERFACE_VERSION = "1.0";

  private final MappingMethods mappingMethods = new MappingMethods();

  @Test
  void mapModuleEntitiesFromAppDescriptor_positive_bothBackendAndUi() {
    var backendModule = createBackendModule();
    var uiModule = createUiModule();
    var backendDescriptor = createBackendDescriptor();
    var uiDescriptor = createUiDescriptor();

    var appDescriptor = createAppDescriptorWithBackendAndUi(
      backendModule, uiModule, backendDescriptor, uiDescriptor
    );

    var result = mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor);

    assertThat(result).hasSize(2);

    var backendEntity = findModuleById(result, BACKEND_MODULE_ID);
    assertBackendModule(backendEntity, backendDescriptor);

    var uiEntity = findModuleById(result, UI_MODULE_ID);
    assertUiModule(uiEntity, uiDescriptor);
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_positive_onlyBackendModules() {
    var backendModule = createBackendModule();
    var backendDescriptor = createBackendDescriptor();

    var appDescriptor = createAppDescriptorWithBackend(backendModule, backendDescriptor);

    var result = mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor);

    assertThat(result).hasSize(1);

    var backendEntity = result.iterator().next();
    assertThat(backendEntity.getId()).isEqualTo(BACKEND_MODULE_ID);
    assertThat(backendEntity.getType()).isEqualTo(ModuleType.BACKEND);
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_positive_onlyUiModules() {
    var uiModule = createUiModule();
    var uiDescriptor = createUiDescriptor();

    var appDescriptor = createAppDescriptorWithUi(uiModule, uiDescriptor);

    var result = mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor);

    assertThat(result).hasSize(1);

    var uiEntity = result.iterator().next();
    assertThat(uiEntity.getId()).isEqualTo(UI_MODULE_ID);
    assertThat(uiEntity.getType()).isEqualTo(ModuleType.UI);
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_positive_emptyWhenNoModules() {
    var appDescriptor = new ApplicationDescriptor();

    var result = mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor);

    assertThat(result).isEmpty();
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_negative_backendModuleDescriptorNotFound() {
    var backendModule = createBackendModule();

    var appDescriptor = new ApplicationDescriptor()
      .modules(List.of(backendModule))
      .moduleDescriptors(List.of()); // Missing descriptor

    assertThatThrownBy(() -> mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Module descriptor not found for module with id: " + BACKEND_MODULE_ID);
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_negative_uiModuleDescriptorNotFound() {
    var uiModule = createUiModule();

    var appDescriptor = new ApplicationDescriptor()
      .uiModules(List.of(uiModule))
      .uiModuleDescriptors(List.of()); // Missing descriptor

    assertThatThrownBy(() -> mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Module descriptor not found for ui module with id: " + UI_MODULE_ID);
  }

  @Test
  void mapModuleEntitiesFromAppDescriptor_positive_withInterfaceReferences() {
    var backendModule = createBackendModule();
    var backendDescriptor = createBackendDescriptorWithInterface();

    var appDescriptor = createAppDescriptorWithBackend(backendModule, backendDescriptor);

    var result = mappingMethods.mapModuleEntitiesFromAppDescriptor(appDescriptor);

    assertThat(result).hasSize(1);

    var backendEntity = result.iterator().next();
    assertThat(backendEntity.getInterfaces()).hasSize(1);

    var interfaceRef = backendEntity.getInterfaces().iterator().next();
    assertThat(interfaceRef.getId()).isEqualTo(INTERFACE_ID);
    assertThat(interfaceRef.getVersion()).isEqualTo(INTERFACE_VERSION);
  }

  private static Module createBackendModule() {
    return new Module().id(BACKEND_MODULE_ID).name(BACKEND_MODULE_NAME).version(MODULE_VERSION);
  }

  private static Module createUiModule() {
    return new Module().id(UI_MODULE_ID).name(UI_MODULE_NAME).version(MODULE_VERSION);
  }

  private static ModuleDescriptor createBackendDescriptor() {
    return new ModuleDescriptor().id(BACKEND_MODULE_ID);
  }

  private static ModuleDescriptor createUiDescriptor() {
    return new ModuleDescriptor().id(UI_MODULE_ID);
  }

  private static ModuleDescriptor createBackendDescriptorWithInterface() {
    return new ModuleDescriptor()
      .id(BACKEND_MODULE_ID)
      .provides(List.of(
        new InterfaceDescriptor()
          .id(INTERFACE_ID)
          .version(INTERFACE_VERSION)
      ));
  }

  private static ApplicationDescriptor createAppDescriptorWithBackendAndUi(Module backendModule, Module uiModule,
    ModuleDescriptor backendDescriptor, ModuleDescriptor uiDescriptor) {
    return new ApplicationDescriptor()
      .modules(List.of(backendModule))
      .uiModules(List.of(uiModule))
      .moduleDescriptors(List.of(backendDescriptor))
      .uiModuleDescriptors(List.of(uiDescriptor));
  }

  private static ApplicationDescriptor createAppDescriptorWithBackend(Module backendModule,
    ModuleDescriptor backendDescriptor) {
    return new ApplicationDescriptor()
      .modules(List.of(backendModule))
      .moduleDescriptors(List.of(backendDescriptor));
  }

  private static ApplicationDescriptor createAppDescriptorWithUi(Module uiModule, ModuleDescriptor uiDescriptor) {
    return new ApplicationDescriptor()
      .uiModules(List.of(uiModule))
      .uiModuleDescriptors(List.of(uiDescriptor));
  }

  private static ModuleEntity findModuleById(Set<ModuleEntity> modules, String moduleId) {
    return modules.stream()
      .filter(m -> m.getId().equals(moduleId))
      .findFirst()
      .orElseThrow();
  }

  private static void assertBackendModule(ModuleEntity entity, ModuleDescriptor expectedDescriptor) {
    assertThat(entity.getType()).isEqualTo(ModuleType.BACKEND);
    assertThat(entity.getDescriptor()).isEqualTo(expectedDescriptor);
  }

  private static void assertUiModule(ModuleEntity entity, ModuleDescriptor expectedDescriptor) {
    assertThat(entity.getType()).isEqualTo(ModuleType.UI);
    assertThat(entity.getDescriptor()).isEqualTo(expectedDescriptor);
  }
}
