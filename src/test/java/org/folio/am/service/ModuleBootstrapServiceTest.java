package org.folio.am.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_INTERFACE_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO1_0_1_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO1_1_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_INTERFACE_ID;
import static org.folio.am.support.TestValues.egressProviderRow;
import static org.folio.am.support.TestValues.moduleBootstrap;
import static org.folio.am.support.TestValues.moduleBootstrapDiscovery;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ModuleBootstrapMapperImpl;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleBootstrapServiceTest {

  private static final String MODULE_FOO2_ID = "test-module-foo-2.0.0";
  private static final String MODULE_FOO_BUILD1_ID = "test-module-foo-1.0.0+1";
  private static final String MODULE_FOO_BUILD2_ID = "test-module-foo-1.0.0+2";

  @Mock private ModuleBootstrapRepository repository;

  private ModuleBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new ModuleBootstrapService(repository, new ModuleBootstrapMapperImpl());
  }

  @Test
  void getById_positive() {
    var expectedView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    expectedView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));

    var expectedDependencyView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID, "not-required-interface");
    var expectedViews = new ArrayList<>(asList(expectedView, expectedDependencyView));

    var expectedModuleDiscovery = moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    var expectedDependencyDiscovery = moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);

    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(expectedViews);

    var actual = service.getById(MODULE_FOO_ID);
    assertThat(actual).isEqualTo(moduleBootstrap(expectedModuleDiscovery, expectedDependencyDiscovery));
  }

  @Test
  void getById_positive_emptyDependencies() {
    var expectedView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);

    var expectedDependencyView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID, "not-required-interface");
    var expectedViews = new ArrayList<>(asList(expectedView, expectedDependencyView));

    var expectedModuleDiscovery = moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);

    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(expectedViews);

    var actual = service.getById(MODULE_FOO_ID);
    assertThat(actual).isEqualTo(moduleBootstrap(expectedModuleDiscovery));
  }

  @Test
  void getById_positive_duplicates() {
    var expectedView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    expectedView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_FOO_ID));
    expectedView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_FOO1_1_ID));

    var expectedDependencyView1 = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID, "not-required-interface");
    var expectedDependencyView2 =
      moduleBootstrapView(MODULE_FOO1_1_ID, MODULE_FOO_INTERFACE_ID, "not-required-interface");
    var expectedDependencyView3 =
      moduleBootstrapView(MODULE_FOO1_0_1_ID, MODULE_FOO_INTERFACE_ID, "not-required-interface");

    when(repository.findAllRequiredByModuleId(MODULE_BAR_ID)).thenReturn(new ArrayList<>(
      List.of(expectedView, expectedDependencyView1, expectedDependencyView2, expectedDependencyView3)));

    var actual = service.getById(MODULE_BAR_ID);
    assertThat(actual.getRequiredModules()).hasSize(1);
    assertThat(actual.getRequiredModules().get(0).getModuleId()).isEqualTo(MODULE_FOO1_1_ID);
  }

  @Test
  void getById_positive_descriptorNotModified() {
    var fooView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    fooView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));
    var barView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID, "not-required-interface");
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(asList(fooView, barView)));

    service.getById(MODULE_FOO_ID);

    assertThat(barView.getDescriptor().getProvides())
      .extracting(InterfaceDescriptor::getId)
      .containsExactly(MODULE_BAR_INTERFACE_ID, "not-required-interface");
  }

  @Test
  void getById_positive_moduleInSeveralApplications_selfExcluded() {
    var fooView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    fooView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_FOO_INTERFACE_ID));
    var barView = moduleBootstrapView(MODULE_BAR_ID, MODULE_FOO_INTERFACE_ID);
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID))
      .thenReturn(new ArrayList<>(asList(fooView, barView, fooView)));

    var actual = service.getById(MODULE_FOO_ID);

    assertThat(actual.getRequiredModules())
      .extracting(ModuleBootstrapDiscovery::getModuleId)
      .containsExactly(MODULE_BAR_ID);
  }

  @Test
  void getById_positive_requiredModulesSortedByModuleId() {
    var fooView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    fooView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));
    fooView.getDescriptor().addRequiresItem(new InterfaceReference().id("test-another-interface"));
    var anotherView = moduleBootstrapView("test-another-module-1.0.0", "test-another-interface");
    var barView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID))
      .thenReturn(new ArrayList<>(asList(fooView, barView, anotherView)));

    var actual = service.getById(MODULE_FOO_ID);

    assertThat(actual.getRequiredModules())
      .extracting(ModuleBootstrapDiscovery::getModuleId)
      .containsExactly("test-another-module-1.0.0", MODULE_BAR_ID);
  }

  @Test
  void getById_positive_sameInterfaceRequiredAndOptional_providerReturnedOnce() {
    var fooView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    fooView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));
    fooView.getDescriptor().addOptionalItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));
    var barView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(asList(fooView, barView)));

    var actual = service.getById(MODULE_FOO_ID);

    assertThat(actual.getRequiredModules())
      .containsExactly(moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID));
  }

  @Test
  void getById_negative_notFound() {
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.getById(MODULE_FOO_ID)).isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + MODULE_FOO_ID);
  }

  @Test
  void getIngressBootstrap_positive() {
    var fooView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    when(repository.findViewsById(MODULE_FOO_ID)).thenReturn(new ArrayList<>(List.of(fooView)));

    var actual = service.getIngressBootstrap(MODULE_FOO_ID);

    assertThat(actual.getModule()).isEqualTo(moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID));
    assertThat(actual.getRequiredModules()).isEmpty();
  }

  @Test
  void getIngressBootstrap_negative_notFound() {
    when(repository.findViewsById(MODULE_FOO_ID)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.getIngressBootstrap(MODULE_FOO_ID))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + MODULE_FOO_ID);
  }

  @Test
  void getEgressBootstrap_positive() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope))
      .thenReturn(List.of(egressProviderRow(MODULE_BAR_ID, APPLICATION_ID, MODULE_BAR_INTERFACE_ID)));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules())
      .containsExactly(moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID));
  }

  @Test
  void getEgressBootstrap_positive_noProviders() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of());

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "app-1.10.0, app-1.2.0, app-1.10.0",
    "app-10.0.0, app-2.0.0, app-10.0.0",
    "app-1.0.0, app-1.0.0-SNAPSHOT.5, app-1.0.0",
    "app-1.0.0-SNAPSHOT.10, app-1.0.0-SNAPSHOT.9, app-1.0.0-SNAPSHOT.10",
    "app-b-1.0.0, app-a-2.0.0, app-b-1.0.0"
  })
  void getEgressBootstrap_positive_providerInTwoApplications_highestApplicationVersion(String first, String second,
    String expectedApplicationId) {
    var scope = Stream.of(first, second).sorted().toList();
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of(
      egressProviderRow(MODULE_BAR_ID, first, MODULE_BAR_INTERFACE_ID),
      egressProviderRow(MODULE_BAR_ID, second, MODULE_BAR_INTERFACE_ID)));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, List.of(first, second));

    assertThat(actual.getRequiredModules())
      .singleElement()
      .extracting(ModuleBootstrapDiscovery::getApplicationId)
      .isEqualTo(expectedApplicationId);
  }

  @Test
  void getEgressBootstrap_positive_duplicateRowsFoldedToOneInterface() {
    var scope = List.of("app-a-1.0.0", "app-b-1.0.0");
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of(
      egressProviderRow(MODULE_BAR_ID, "app-a-1.0.0", MODULE_BAR_INTERFACE_ID),
      egressProviderRow(MODULE_BAR_ID, "app-b-1.0.0", MODULE_BAR_INTERFACE_ID)));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules())
      .singleElement()
      .satisfies(provider -> assertThat(provider.getInterfaces()).hasSize(1));
  }

  @Test
  void getEgressBootstrap_positive_interfacesSortedById() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of(
      egressProviderRow(MODULE_BAR_ID, APPLICATION_ID, "interface-b"),
      egressProviderRow(MODULE_BAR_ID, APPLICATION_ID, "interface-c"),
      egressProviderRow(MODULE_BAR_ID, APPLICATION_ID, "interface-a")));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules())
      .singleElement()
      .satisfies(provider -> assertThat(provider.getInterfaces())
        .extracting(ModuleBootstrapInterface::getId)
        .containsExactly("interface-a", "interface-b", "interface-c"));
  }

  @Test
  void getEgressBootstrap_positive_multipleInterfaceProvidedByDifferentModules_allReturned() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of(
      egressProviderRow(MODULE_BAR_ID, APPLICATION_ID, MODULE_BAR_INTERFACE_ID),
      egressProviderRow("test-module-baz-1.0.0", APPLICATION_ID, MODULE_BAR_INTERFACE_ID)));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules()).containsExactly(
      moduleBootstrapDiscovery(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID),
      moduleBootstrapDiscovery("test-module-baz-1.0.0", MODULE_BAR_INTERFACE_ID));
    assertThat(actual.getRequiredModules())
      .flatExtracting(ModuleBootstrapDiscovery::getInterfaces)
      .extracting(ModuleBootstrapInterface::getInterfaceType)
      .containsOnly("multiple");
  }

  @Test
  void getEgressBootstrap_positive_requiredModulesSortedByModuleId() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_FOO_ID, scope);
    when(repository.findEgressProviders(MODULE_FOO_ID, scope)).thenReturn(List.of(
      egressProviderRow("test-module-zzz-1.0.0", APPLICATION_ID, "interface-z"),
      egressProviderRow("test-module-aaa-1.0.0", APPLICATION_ID, "interface-a")));

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, scope);

    assertThat(actual.getRequiredModules())
      .extracting(ModuleBootstrapDiscovery::getModuleId)
      .containsExactly("test-module-aaa-1.0.0", "test-module-zzz-1.0.0");
  }

  @Test
  void getEgressBootstrap_positive_highestVersionPerNameRegardlessOfRowOrder() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_BAR_ID, scope);
    var versionRows = List.of(
      egressProviderRow(MODULE_FOO_ID, APPLICATION_ID, MODULE_FOO_INTERFACE_ID),
      egressProviderRow(MODULE_FOO2_ID, APPLICATION_ID, MODULE_FOO_INTERFACE_ID),
      egressProviderRow(MODULE_FOO1_1_ID, APPLICATION_ID, MODULE_FOO_INTERFACE_ID));
    when(repository.findEgressProviders(MODULE_BAR_ID, scope))
      .thenReturn(versionRows, List.of(versionRows.get(2), versionRows.get(1), versionRows.get(0)));

    var first = service.getEgressBootstrap(MODULE_BAR_ID, scope);
    var second = service.getEgressBootstrap(MODULE_BAR_ID, scope);

    assertThat(first.getRequiredModules()).extracting(ModuleBootstrapDiscovery::getModuleId)
      .containsExactly(MODULE_FOO2_ID);
    assertThat(second).isEqualTo(first);
  }

  @Test
  void getEgressBootstrap_positive_equalVersionsTieBrokenByModuleId() {
    var scope = List.of(APPLICATION_ID);
    mockModuleInScope(MODULE_BAR_ID, scope);
    when(repository.findEgressProviders(MODULE_BAR_ID, scope)).thenReturn(List.of(
      egressProviderRow(MODULE_FOO_BUILD2_ID, APPLICATION_ID, MODULE_FOO_INTERFACE_ID),
      egressProviderRow(MODULE_FOO_BUILD1_ID, APPLICATION_ID, MODULE_FOO_INTERFACE_ID)));

    var actual = service.getEgressBootstrap(MODULE_BAR_ID, scope);

    assertThat(actual.getRequiredModules()).extracting(ModuleBootstrapDiscovery::getModuleId)
      .containsExactly(MODULE_FOO_BUILD1_ID);
  }

  @Test
  void getEgressBootstrap_positive_applicationIdsNormalized() {
    var normalizedScope = List.of("app-a-1.0.0", "app-b-1.0.0");
    mockModuleInScope(MODULE_FOO_ID, normalizedScope);
    when(repository.findEgressProviders(MODULE_FOO_ID, normalizedScope)).thenReturn(List.of());

    var actual = service.getEgressBootstrap(MODULE_FOO_ID, List.of("app-b-1.0.0", "app-a-1.0.0", "app-b-1.0.0"));

    assertThat(actual.getRequiredModules()).isEmpty();
  }

  @Test
  void getEgressBootstrap_negative_moduleNotInScope() {
    var scope = List.of("other-app-1.0.0");
    when(repository.existsByIdAndApplicationIdIn(MODULE_FOO_ID, scope)).thenReturn(false);

    assertThatThrownBy(() -> service.getEgressBootstrap(MODULE_FOO_ID, scope))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + MODULE_FOO_ID);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void getEgressBootstrap_negative_blankApplicationId(String blankId) {
    var scope = Arrays.asList(APPLICATION_ID, blankId);

    assertThatThrownBy(() -> service.getEgressBootstrap(MODULE_FOO_ID, scope))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application id must not be blank");
    verifyNoInteractions(repository);
  }

  private void mockModuleInScope(String moduleId, List<String> scope) {
    when(repository.existsByIdAndApplicationIdIn(moduleId, scope)).thenReturn(true);
  }
}
