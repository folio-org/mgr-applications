package org.folio.am.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleEntityTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String ERROR_MSG_ID_NULL = "Module id must not be null";
  private static final String ERROR_MSG_TYPE_NULL = "Module type must not be null";

  @Test
  void of_positive_backendModule() {
    var entity = createBackendModule();

    assertModuleEntity(entity, MODULE_ID, ModuleType.BACKEND);
  }

  @Test
  void of_positive_uiModule() {
    var entity = createUiModule();

    assertModuleEntity(entity, MODULE_ID, ModuleType.UI);
  }

  @Test
  void of_negative_idIsNull() {
    assertThatThrownBy(() -> ModuleEntity.of(null, ModuleType.BACKEND))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining(ERROR_MSG_ID_NULL);
  }

  @Test
  void of_negative_typeIsNull() {
    assertThatThrownBy(() -> ModuleEntity.of(MODULE_ID, null))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining(ERROR_MSG_TYPE_NULL);
  }

  @Test
  void isBackendModule_positive_backendType() {
    var entity = createBackendModule();

    assertThat(entity.isBackendModule()).isTrue();
    assertThat(entity.isUiModule()).isFalse();
  }

  @Test
  void isUiModule_positive_uiType() {
    var entity = createUiModule();

    assertThat(entity.isUiModule()).isTrue();
    assertThat(entity.isBackendModule()).isFalse();
  }

  private static ModuleEntity createBackendModule() {
    return ModuleEntity.of(MODULE_ID, ModuleType.BACKEND);
  }

  private static ModuleEntity createUiModule() {
    return ModuleEntity.of(MODULE_ID, ModuleType.UI);
  }

  private static void assertModuleEntity(ModuleEntity entity, String expectedId, ModuleType expectedType) {
    assertThat(entity.getId()).isEqualTo(expectedId);
    assertThat(entity.getType()).isEqualTo(expectedType);
  }
}
