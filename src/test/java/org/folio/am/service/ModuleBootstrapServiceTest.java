package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.service.ModuleBootstrapData.ResolvedModule;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.folio.am.mapper.ModuleBootstrapMapperImpl;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleBootstrapServiceTest {

  private static final String FOO = "test-module-foo-1.0.0";
  private static final String BAR = "test-module-bar-1.0.0";
  private static final String BAR_V2 = "test-module-bar-2.0.0";
  private static final String BAR_V2_SNAPSHOT = "test-module-bar-2.0.0-SNAPSHOT";
  private static final String BAR_INT = "test-bar-interface";
  private static final String OTHER_INT = "test-other-interface";

  @Mock private ModuleBootstrapDataProvider dataProvider;
  private ModuleBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new ModuleBootstrapService(dataProvider, new ModuleBootstrapMapperImpl());
  }

  private static ResolvedModule resolved(String id, String applicationId, ModuleDescriptor descriptor) {
    return new ResolvedModule(id, "http://" + id + ":8080", false, descriptor, applicationId);
  }

  private static ModuleDescriptor consumerDescriptor() {
    return new ModuleDescriptor().requires(List.of(new InterfaceReference().id(BAR_INT)));
  }

  private static ModuleDescriptor providerDescriptor() {
    return new ModuleDescriptor().provides(List.of(new InterfaceDescriptor().id(BAR_INT)
      .interfaceType("multiple").addHandlersItem(new RoutingEntry().addMethodsItem("GET").path("/x"))));
  }

  private static ModuleDescriptor providerWithExtraInterface() {
    return new ModuleDescriptor().provides(List.of(
      new InterfaceDescriptor().id(BAR_INT).interfaceType("multiple")
        .addHandlersItem(new RoutingEntry().addMethodsItem("GET").path("/x")),
      new InterfaceDescriptor().id(OTHER_INT).interfaceType("multiple")
        .addHandlersItem(new RoutingEntry().addMethodsItem("GET").path("/y"))));
  }

  @Test
  void getById_returnsModuleAndRequiredProviders() {
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var provider = resolved(BAR, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getById(FOO);

    assertThat(actual.getModule().getModuleId()).isEqualTo(FOO);
    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR));
  }

  @Test
  void getById_throws_whenModuleAbsent() {
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(null, List.of()));
    assertThatThrownBy(() -> service.getById(FOO))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + FOO);
  }

  @Test
  void getIngressBootstrap_returnsModuleOnly() {
    var self = resolved(FOO, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of()));

    var actual = service.getIngressBootstrap(FOO);

    assertThat(actual.getModule().getModuleId()).isEqualTo(FOO);
    assertThat(actual.getRequiredModules()).isEmpty();
  }

  @Test
  void getEgressBootstrap_emptyApplicationIds_returnsNotFound() {
    var actual = service.getEgressBootstrap(FOO, List.of());
    assertThat(actual.getFound()).isFalse();
  }

  @Test
  void getEgressBootstrap_selectsInScopeProviderVersion_acrossApplications() {
    // same provider NAME in two apps as two VERSIONS (distinct ids); only the app-b version is in scope
    var self = resolved(FOO, "app-consumer-1.0.0", consumerDescriptor());
    var providerInA = resolved(BAR, "app-a-1.0.0", providerDescriptor());
    var providerInB = resolved(BAR_V2, "app-b-1.0.0", providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(providerInA, providerInB)));

    var actual = service.getEgressBootstrap(FOO, List.of("app-consumer-1.0.0", "app-b-1.0.0"));

    assertThat(actual.getFound()).isTrue();
    assertThat(actual.getBootstrap()).isNotNull();
    assertThat(actual.getBootstrap().getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2));
  }

  @Test
  void getEgressBootstrap_selfOutsideScope_returnsNotFound() {
    var self = resolved(FOO, "app-a-1.0.0", consumerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of()));

    var actual = service.getEgressBootstrap(FOO, List.of("app-b-1.0.0"));
    assertThat(actual.getFound()).isFalse();
  }

  @Test
  void getEgressBootstrap_nullApplicationIds_returnsNotFound() {
    var actual = service.getEgressBootstrap(FOO, null);
    assertThat(actual.getFound()).isFalse();
  }

  @Test
  void getEgressBootstrap_selfRequiresNoInterfaces_returnsFoundWithEmptyRequiredModules() {
    var self = resolved(FOO, "app-consumer-1.0.0", providerDescriptor()); // provides only, requires nothing
    var provider = resolved(BAR, "app-consumer-1.0.0", providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getEgressBootstrap(FOO, List.of("app-consumer-1.0.0"));

    assertThat(actual.getFound()).isTrue();
    assertThat(actual.getBootstrap()).isNotNull();
    assertThat(actual.getBootstrap().getRequiredModules()).isEmpty();
  }

  @Test
  void getById_dedupesSameNameProviders_keepingHighestVersion() {
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var providerV1 = resolved(BAR, APPLICATION_ID, providerDescriptor());
    var providerV2 = resolved(BAR_V2, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(providerV1, providerV2)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2));
  }

  @Test
  void getById_dedupesSameNameProviders_keepingHighestVersion_reversedOrder() {
    // Identical to the test above, but the highest version comes FIRST in the provider list.
    // The javadoc on getById promises the "highest-version" provider across all applications,
    // independent of list order (findAllRequiredByModuleId has no ORDER BY).
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var providerV2 = resolved(BAR_V2, APPLICATION_ID, providerDescriptor());
    var providerV1 = resolved(BAR, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(providerV2, providerV1)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2));
  }

  @Test
  void getById_dedupesReleaseAndSnapshotOfSameModule_keepsRelease() {
    // A release outranks the snapshot of the same version (2.0.0 > 2.0.0-SNAPSHOT); the two must be recognised as
    // the same module name (snapshot/build suffixes contain '-', which naive last-dash splitting mis-handles).
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var snapshot = resolved(BAR_V2_SNAPSHOT, APPLICATION_ID, providerDescriptor());
    var release = resolved(BAR_V2, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(snapshot, release)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2));
  }

  @Test
  void getById_dedupesReleaseAndSnapshotOfSameModule_keepsRelease_reversedOrder() {
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var release = resolved(BAR_V2, APPLICATION_ID, providerDescriptor());
    var snapshot = resolved(BAR_V2_SNAPSHOT, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(release, snapshot)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2));
  }

  @Test
  void getById_dedupesHigherSnapshotMajorOverLowerRelease_keepsSnapshot() {
    // Snapshot of a higher version still wins over a lower release (2.0.0-SNAPSHOT > 1.0.0).
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var release = resolved(BAR, APPLICATION_ID, providerDescriptor());
    var snapshot = resolved(BAR_V2_SNAPSHOT, APPLICATION_ID, providerDescriptor());
    when(dataProvider.getData(FOO))
      .thenReturn(new ModuleBootstrapData(self, List.of(release, snapshot)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR_V2_SNAPSHOT));
  }

  @Test
  void getById_narrowsProviderInterfacesToRequiredOnly() {
    var self = resolved(FOO, APPLICATION_ID, consumerDescriptor());
    var provider = resolved(BAR, APPLICATION_ID, providerWithExtraInterface());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getById(FOO);

    assertThat(actual.getRequiredModules()).singleElement().satisfies(m ->
      assertThat(m.getInterfaces()).extracting("id").containsExactly(BAR_INT));
  }
}
