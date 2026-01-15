package org.folio.am.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ApplicationEntityTest {

  private static final String APPLICATION_ID = "app-1.0.0";
  private static final String BACKEND_MODULE_ID_1 = "mod-backend-1";
  private static final String BACKEND_MODULE_ID_2 = "mod-backend-2";
  private static final String UI_MODULE_ID_1 = "mod-ui-1";
  private static final String UI_MODULE_ID_2 = "mod-ui-2";

  @Test
  void getBackendModules_positive_filtersByType() {
    var backendModule1 = createBackendModule(BACKEND_MODULE_ID_1);
    var backendModule2 = createBackendModule(BACKEND_MODULE_ID_2);
    var uiModule1 = createUiModule(UI_MODULE_ID_1);
    var uiModule2 = createUiModule(UI_MODULE_ID_2);

    var application = createApplicationWithModules(backendModule1, backendModule2, uiModule1, uiModule2);

    var backendModules = application.getBackendModules();

    assertThat(backendModules).hasSize(2);
    assertThat(backendModules).containsExactlyInAnyOrder(backendModule1, backendModule2);
  }

  @Test
  void getBackendModules_positive_emptyWhenOnlyUiModules() {
    var uiModule1 = createUiModule(UI_MODULE_ID_1);
    var uiModule2 = createUiModule(UI_MODULE_ID_2);

    var application = createApplicationWithModules(uiModule1, uiModule2);

    var backendModules = application.getBackendModules();

    assertThat(backendModules).isEmpty();
  }

  @Test
  void getUiModules_positive_filtersByType() {
    var backendModule1 = createBackendModule(BACKEND_MODULE_ID_1);
    var backendModule2 = createBackendModule(BACKEND_MODULE_ID_2);
    var uiModule1 = createUiModule(UI_MODULE_ID_1);
    var uiModule2 = createUiModule(UI_MODULE_ID_2);

    var application = createApplicationWithModules(backendModule1, backendModule2, uiModule1, uiModule2);

    var uiModules = application.getUiModules();

    assertThat(uiModules).hasSize(2);
    assertThat(uiModules).containsExactlyInAnyOrder(uiModule1, uiModule2);
  }

  @Test
  void getUiModules_positive_emptyWhenOnlyBackendModules() {
    var backendModule1 = createBackendModule(BACKEND_MODULE_ID_1);
    var backendModule2 = createBackendModule(BACKEND_MODULE_ID_2);

    var application = createApplicationWithModules(backendModule1, backendModule2);

    var uiModules = application.getUiModules();

    assertThat(uiModules).isEmpty();
  }

  @Test
  void getBackendModules_positive_emptyWhenNoModules() {
    var application = createApplication();

    var backendModules = application.getBackendModules();

    assertThat(backendModules).isEmpty();
  }

  @Test
  void getUiModules_positive_emptyWhenNoModules() {
    var application = createApplication();

    var uiModules = application.getUiModules();

    assertThat(uiModules).isEmpty();
  }

  private static ModuleEntity createBackendModule(String moduleId) {
    return ModuleEntity.of(moduleId, ModuleType.BACKEND);
  }

  private static ModuleEntity createUiModule(String moduleId) {
    return ModuleEntity.of(moduleId, ModuleType.UI);
  }

  private static ApplicationEntity createApplication() {
    return ApplicationEntity.of(APPLICATION_ID);
  }

  private static ApplicationEntity createApplicationWithModules(ModuleEntity... modules) {
    var application = createApplication();
    application.setModules(Set.of(modules));
    return application;
  }
}
