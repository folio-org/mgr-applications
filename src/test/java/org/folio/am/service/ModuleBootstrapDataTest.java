package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestValues.moduleBootstrapView;

import java.util.List;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleBootstrapDataTest {

  @Test
  void from_splitsSelfFromProviders_andReadsApplicationIdPerRow() {
    var self = moduleBootstrapView("mod-foo-1.0.0", "foo-int");
    var provider = viewInApp("mod-bar-1.0.0", "app-bar-1.0.0");

    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(self, provider));

    assertThat(data.self()).isNotNull();
    assertThat(data.self().id()).isEqualTo("mod-foo-1.0.0");
    assertThat(data.self().applicationId()).isEqualTo(APPLICATION_ID);
    assertThat(data.providers()).singleElement().satisfies(p -> {
      assertThat(p.id()).isEqualTo("mod-bar-1.0.0");
      assertThat(p.applicationId()).isEqualTo("app-bar-1.0.0");
    });
  }

  @Test
  void from_returnsNullSelf_whenModuleAbsent() {
    var provider = moduleBootstrapView("mod-bar-1.0.0", "bar-int");
    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(provider));

    assertThat(data.self()).isNull();
    assertThat(data.providers()).hasSize(1);
  }

  private static ModuleBootstrapView viewInApp(String id, String applicationId) {
    var view = moduleBootstrapView(id, "x-int");
    view.setApplicationId(applicationId);
    return view;
  }
}
