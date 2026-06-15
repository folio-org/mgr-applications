package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestValues.moduleBootstrapView;

import java.util.List;
import org.folio.am.domain.entity.ModuleApplicationId;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleBootstrapDataTest {

  private static ModuleApplicationId appId(String id, String applicationId) {
    return new ModuleApplicationId() {
      @Override public String getId() {
        return id;
      }

      @Override public String getApplicationId() {
        return applicationId;
      }
    };
  }

  @Test
  void from_groupsSelfAndProviders_withFullApplicationSets() {
    var self = moduleBootstrapView("mod-foo-1.0.0", "foo-int");
    var provider = moduleBootstrapView("mod-bar-1.0.0", "bar-int");
    var appIdRows = List.of(
      appId("mod-foo-1.0.0", "app-a-1.0.0"),
      appId("mod-bar-1.0.0", "app-a-1.0.0"),
      appId("mod-bar-1.0.0", "app-b-1.0.0")); // shared provider

    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(self, provider), appIdRows);

    assertThat(data.self()).isNotNull();
    assertThat(data.self().id()).isEqualTo("mod-foo-1.0.0");
    assertThat(data.self().applicationIds()).containsExactly("app-a-1.0.0");
    assertThat(data.providers()).singleElement()
      .satisfies(p -> {
        assertThat(p.id()).isEqualTo("mod-bar-1.0.0");
        assertThat(p.applicationIds()).containsExactlyInAnyOrder("app-a-1.0.0", "app-b-1.0.0");
      });
  }

  @Test
  void from_returnsNullSelf_whenModuleAbsent() {
    var provider = moduleBootstrapView("mod-bar-1.0.0", "bar-int");
    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(provider),
      List.of(appId("mod-bar-1.0.0", "app-a-1.0.0")));

    assertThat(data.self()).isNull();
    assertThat(data.providers()).hasSize(1);
  }
}
