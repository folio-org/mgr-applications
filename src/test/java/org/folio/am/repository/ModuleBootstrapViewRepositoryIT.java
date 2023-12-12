package org.folio.am.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.util.function.Predicate;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.support.base.BaseRepositoryTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@Sql(scripts =
  {
    "classpath:/sql/application-descriptor-with-routes.sql",
    "classpath:/sql/module-interface-references.sql"
  }, executionPhase = BEFORE_TEST_METHOD)
@Sql(
  scripts = "classpath:/sql/truncate-tables.sql",
  executionPhase = AFTER_TEST_METHOD
)
class ModuleBootstrapViewRepositoryIT extends BaseRepositoryTest {

  private static final String MODULE_FOO_APP_ID = "test-app-1.0.0";
  private static final String MODULE_BAR_APP_ID = "test-app-2.0.0";
  private static final String MODULE_FOO_ID = "test-module-foo-1.0.0";
  private static final String MODULE_BAR_ID = "test-module-bar-1.0.0";
  private static final String MODULE_FOO_DISCOVERY_URL = "http://test-module-foo:8080";
  private static final String MODULE_BAR_DISCOVERY_URL = "http://test-module-bar:8080";

  @Autowired
  private ModuleBootstrapViewRepository repository;

  @Test
  void shouldReturnAllRequiredModules() {
    var result = repository.findAllRequiredByModuleId(MODULE_FOO_ID);
    assertThat(result)
      .hasSize(2)
      .anyMatch(matchView(MODULE_FOO_ID, MODULE_FOO_APP_ID, MODULE_FOO_DISCOVERY_URL))
      .anyMatch(matchView(MODULE_BAR_ID, MODULE_BAR_APP_ID, MODULE_BAR_DISCOVERY_URL));
  }

  @Test
  void shouldReturnOnlyModuleIfNoDependencies() {
    var result = repository.findAllRequiredByModuleId(MODULE_BAR_ID);
    assertThat(result)
      .hasSize(1)
      .anyMatch(matchView(MODULE_BAR_ID, MODULE_BAR_APP_ID, MODULE_BAR_DISCOVERY_URL));
  }

  private Predicate<ModuleBootstrapView> matchView(String moduleId, String appId, String location) {
    return view ->
      view.getId().equals(moduleId)
        && view.getApplicationId().equals(appId)
        && view.getLocation().equals(location)
        && view.getDescriptor() != null;
  }
}
