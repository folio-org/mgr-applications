package org.folio.am.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.util.List;
import java.util.function.Predicate;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.domain.model.EgressProviderRow;
import org.folio.am.support.base.BaseRepositoryTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ModuleBootstrapRepositoryIT extends BaseRepositoryTest {

  private static final String APP_1_0_0_ID = "test-app-1.0.0";
  private static final String APP_2_0_0_ID = "test-app-2.0.0";
  private static final String MODULE_FOO_ID = "test-module-foo-1.0.0";
  private static final String MODULE_BAR_ID = "test-module-bar-1.0.0";
  private static final String MODULE_BAZ_ID = "test-module-baz-1.0.0";
  private static final String MODULE_FOO_DISCOVERY_URL = "http://test-module-foo:8080";
  private static final String MODULE_BAR_DISCOVERY_URL = "http://test-module-bar:8080";
  private static final String MODULE_BAZ_DISCOVERY_URL = "http://test-module-baz:8080";

  @Autowired
  private ModuleBootstrapRepository repository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldReturnAllRequiredModulesWithDiscoveryUrls() {
    var result = repository.findAllRequiredByModuleId(MODULE_FOO_ID);
    assertThat(result)
      .hasSize(3)
      .anyMatch(matchView(MODULE_FOO_ID, APP_1_0_0_ID, MODULE_FOO_DISCOVERY_URL))
      .anyMatch(matchView(MODULE_BAR_ID, APP_2_0_0_ID, MODULE_BAR_DISCOVERY_URL))
      .anyMatch(matchView(MODULE_BAZ_ID, APP_2_0_0_ID, MODULE_BAZ_DISCOVERY_URL));

    moduleRepository.findById(MODULE_BAZ_ID).stream().peek(moduleEntity -> moduleEntity.setDiscoveryUrl(null))
      .forEach(moduleRepository::save);
    moduleRepository.flush();

    result = repository.findAllRequiredByModuleId(MODULE_FOO_ID);
    assertThat(result)
      .hasSize(2)
      .anyMatch(matchView(MODULE_FOO_ID, APP_1_0_0_ID, MODULE_FOO_DISCOVERY_URL))
      .anyMatch(matchView(MODULE_BAR_ID, APP_2_0_0_ID, MODULE_BAR_DISCOVERY_URL));
  }

  @Test
  void shouldReturnOnlyModuleIfNoDependencies() {
    var result = repository.findAllRequiredByModuleId(MODULE_BAR_ID);
    assertThat(result)
      .hasSize(1)
      .anyMatch(matchView(MODULE_BAR_ID, APP_2_0_0_ID, MODULE_BAR_DISCOVERY_URL));
  }

  @Test
  void findAllRequiredByModuleId_returnsLowestApplicationIdForSharedModule() {
    addModuleToApplication(MODULE_BAR_ID, APP_1_0_0_ID);

    var result = repository.findAllRequiredByModuleId(MODULE_FOO_ID);

    // the module is returned once per owning application, but all entries share one instance populated from the
    // first row, which is the one with the lowest application id
    assertThat(result).filteredOn(view -> view.getId().equals(MODULE_BAR_ID))
      .hasSize(2)
      .allSatisfy(view -> assertThat(view.getApplicationId()).isEqualTo(APP_1_0_0_ID));
  }

  @Test
  void findEgressProviders_returnsOnlyInScopeProviders() {
    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_2_0_0_ID));

    assertThat(rows)
      .extracting(EgressProviderRow::moduleId, EgressProviderRow::applicationId)
      .containsOnly(tuple(MODULE_BAR_ID, APP_2_0_0_ID), tuple(MODULE_BAZ_ID, APP_2_0_0_ID));
  }

  @Test
  void findEgressProviders_noProvidersWhenNoneInScope() {
    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_1_0_0_ID));

    assertThat(rows).isEmpty();
  }

  @Test
  void findEgressProviders_returnsOnlyRequiredInterfaces() {
    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_1_0_0_ID, APP_2_0_0_ID));

    assertThat(rows)
      .extracting(EgressProviderRow::moduleId, row -> row.providedInterface().getId())
      .containsExactlyInAnyOrder(
        tuple(MODULE_BAR_ID, "test-bar-interface"),
        tuple(MODULE_BAR_ID, "test-bar-interface-2"),
        tuple(MODULE_BAZ_ID, "test-baz-interface"));
  }

  @Test
  void findEgressProviders_returnsProvidedInterfaceWithHandlersAndLocation() {
    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_2_0_0_ID));

    assertThat(rows)
      .filteredOn(row -> row.providedInterface().getId().equals("test-bar-interface"))
      .singleElement()
      .satisfies(row -> {
        assertThat(row.location()).isEqualTo(MODULE_BAR_DISCOVERY_URL);
        assertThat(row.providedInterface().getInterfaceType()).isEqualTo("multiple");
        assertThat(row.providedInterface().getHandlers()).singleElement().satisfies(handler -> {
          assertThat(handler.getMethods()).containsExactly("GET");
          assertThat(handler.getPathPattern()).isEqualTo("/bar");
          assertThat(handler.getPermissionsDesired()).containsExactly("bar.get");
        });
      });
  }

  @Test
  void findEgressProviders_excludesSelfAndProvidersWithoutDiscovery() {
    clearDiscoveryUrl(MODULE_BAZ_ID);

    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_1_0_0_ID, APP_2_0_0_ID));

    assertThat(rows).extracting(EgressProviderRow::moduleId)
      .doesNotContain(MODULE_FOO_ID, MODULE_BAZ_ID)
      .containsOnly(MODULE_BAR_ID);
  }

  @Test
  void findEgressProviders_rowPerApplication() {
    addModuleToApplication(MODULE_BAR_ID, APP_1_0_0_ID);

    var rows = repository.findEgressProviders(MODULE_FOO_ID, List.of(APP_1_0_0_ID, APP_2_0_0_ID));

    assertThat(rows).filteredOn(row -> row.moduleId().equals(MODULE_BAR_ID))
      .extracting(EgressProviderRow::applicationId)
      .containsExactlyInAnyOrder(APP_1_0_0_ID, APP_1_0_0_ID, APP_2_0_0_ID, APP_2_0_0_ID);
  }

  @Test
  void existsByIdAndApplicationIdIn_trueForInScopeBackendModule() {
    assertThat(repository.existsByIdAndApplicationIdIn(MODULE_FOO_ID, List.of(APP_1_0_0_ID, APP_2_0_0_ID))).isTrue();
  }

  @Test
  void existsByIdAndApplicationIdIn_falseForOutOfScopeModule() {
    assertThat(repository.existsByIdAndApplicationIdIn(MODULE_FOO_ID, List.of(APP_2_0_0_ID))).isFalse();
  }

  @Test
  void existsByIdAndApplicationIdIn_falseForUiModule() {
    jdbcTemplate.update("INSERT INTO module(id, name, version, descriptor, type) "
      + "VALUES ('test-ui-1.0.0', 'test-ui', '1.0.0', '{}', 'UI')");
    addModuleToApplication("test-ui-1.0.0", APP_1_0_0_ID);

    assertThat(repository.existsByIdAndApplicationIdIn("test-ui-1.0.0", List.of(APP_1_0_0_ID))).isFalse();
  }

  @Test
  void moduleUpdate_discoveryOnly_descriptorNotWritten() {
    var module = moduleRepository.findById(MODULE_FOO_ID).orElseThrow();
    jdbcTemplate.update("UPDATE module SET descriptor = '{\"id\": \"written-by-another-transaction\"}' WHERE id = ?",
      MODULE_FOO_ID);

    module.setDiscoveryUrl("http://test-module-foo:9090");
    moduleRepository.saveAndFlush(module);

    var descriptor = jdbcTemplate.queryForObject("SELECT descriptor::text FROM module WHERE id = ?", String.class,
      MODULE_FOO_ID);
    var discoveryUrl = jdbcTemplate.queryForObject("SELECT discovery_url FROM module WHERE id = ?", String.class,
      MODULE_FOO_ID);
    assertThat(descriptor).contains("written-by-another-transaction");
    assertThat(discoveryUrl).isEqualTo("http://test-module-foo:9090");
  }

  @Test
  void findViewsById_returnsSelfRow() {
    var result = repository.findViewsById(MODULE_FOO_ID);
    assertThat(result)
      .hasSize(1)
      .anyMatch(matchView(MODULE_FOO_ID, APP_1_0_0_ID, MODULE_FOO_DISCOVERY_URL));
  }

  @Test
  void findAllRequiredByModuleId_returnsProviderOnceWhenItProvidesMultipleRequiredInterfaces() {
    // test-module-bar provides both test-bar-interface and test-bar-interface-2, and test-module-foo requires both;
    // the provider must appear once (the IN subquery is a semi-join), not be fanned out now that DISTINCT is gone.
    var result = repository.findAllRequiredByModuleId(MODULE_FOO_ID);

    assertThat(result).hasSize(3);
    assertThat(result).filteredOn(view -> view.getId().equals(MODULE_BAR_ID)).hasSize(1);
    assertNoDuplicateRows(result);
  }

  private void addModuleToApplication(String moduleId, String applicationId) {
    jdbcTemplate.update("INSERT INTO application_module(application_id, module_id) VALUES (?, ?)",
      applicationId, moduleId);
  }

  private void clearDiscoveryUrl(String moduleId) {
    jdbcTemplate.update("UPDATE module SET discovery_url = NULL WHERE id = ?", moduleId);
  }

  private static void assertNoDuplicateRows(List<ModuleBootstrapView> views) {
    assertThat(views)
      .extracting(view -> view.getId() + "@" + view.getApplicationId())
      .doesNotHaveDuplicates();
  }

  private Predicate<ModuleBootstrapView> matchView(String moduleId, String appId, String location) {
    return view ->
      view.getId().equals(moduleId)
        && view.getApplicationId().equals(appId)
        && view.getLocation().equals(location)
        && view.getDescriptor() != null;
  }
}
