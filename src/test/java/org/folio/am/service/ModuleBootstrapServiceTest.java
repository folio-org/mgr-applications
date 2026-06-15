package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.service.ModuleBootstrapData.ResolvedModule;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
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
  private static final String BAR_INT = "test-bar-interface";

  @Mock private ModuleBootstrapDataProvider dataProvider;
  private ModuleBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new ModuleBootstrapService(dataProvider, new ModuleBootstrapMapperImpl());
  }

  private static ResolvedModule resolved(String id, Set<String> apps, ModuleDescriptor descriptor) {
    return new ResolvedModule(id, "http://" + id + ":8080", false, descriptor, apps);
  }

  private static ModuleDescriptor consumerDescriptor() {
    return new ModuleDescriptor().requires(List.of(new InterfaceReference().id(BAR_INT)));
  }

  private static ModuleDescriptor providerDescriptor() {
    return new ModuleDescriptor().provides(List.of(new InterfaceDescriptor().id(BAR_INT)
      .interfaceType("multiple").addHandlersItem(new RoutingEntry().addMethodsItem("GET").path("/x"))));
  }

  @Test
  void getById_returnsModuleAndRequiredProviders() {
    var self = resolved(FOO, Set.of(APPLICATION_ID), consumerDescriptor());
    var provider = resolved(BAR, Set.of(APPLICATION_ID), providerDescriptor());
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
    var self = resolved(FOO, Set.of(APPLICATION_ID), providerDescriptor());
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
  void getEgressBootstrap_sharedProviderInScopeViaSecondApp_isIncluded() {
    var self = resolved(FOO, Set.of("app-a-1.0.0"), consumerDescriptor());
    // shared provider belongs to BOTH apps; scope contains only the second
    var provider = resolved(BAR, Set.of("app-a-1.0.0", "app-b-1.0.0"), providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getEgressBootstrap(FOO, List.of("app-a-1.0.0"));

    assertThat(actual.getFound()).isTrue();
    assertThat(actual.getBootstrap().getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR));
  }

  @Test
  void getEgressBootstrap_selfOutsideScope_returnsNotFound() {
    var self = resolved(FOO, Set.of("app-a-1.0.0"), consumerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of()));

    var actual = service.getEgressBootstrap(FOO, List.of("app-b-1.0.0"));
    assertThat(actual.getFound()).isFalse();
  }
}
