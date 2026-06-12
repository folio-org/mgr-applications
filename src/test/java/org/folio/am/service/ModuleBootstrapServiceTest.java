package org.folio.am.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_INTERFACE_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO1_0_1_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO1_1_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_INTERFACE_ID;
import static org.folio.am.support.TestValues.moduleBootstrap;
import static org.folio.am.support.TestValues.moduleBootstrapDiscovery;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.folio.am.mapper.ModuleBootstrapMapperImpl;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleBootstrapServiceTest {

  @Mock private ModuleBootstrapRepository repository;
  @Mock private ApplicationClosureResolver applicationClosureResolver;

  private ModuleBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new ModuleBootstrapService(repository, new ModuleBootstrapMapperImpl(), applicationClosureResolver);
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
    assertThat(actual.getRequiredModules().getFirst().getModuleId()).isEqualTo(MODULE_FOO1_1_ID);
  }

  @Test
  void getById_negative_notFound() {
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.getById(MODULE_FOO_ID)).isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + MODULE_FOO_ID);
  }

  @Test
  void getById_withApplicationId_resolvesClosureAndCallsScopedQuery() {
    var applicationId = "app-platform-minimal-2.0.53";
    var depAppId = "app-platform-base-0.5.0";
    var closure = Set.of(applicationId, depAppId);

    var moduleView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    moduleView.getDescriptor().addRequiresItem(new InterfaceReference().id(MODULE_BAR_INTERFACE_ID));
    var depView = moduleBootstrapView(MODULE_BAR_ID, MODULE_BAR_INTERFACE_ID);
    var views = new ArrayList<>(asList(moduleView, depView));

    when(applicationClosureResolver.resolve(Set.of(applicationId))).thenReturn(closure);
    when(repository.findAllRequiredByModuleIdAndApplicationIds(MODULE_FOO_ID, closure)).thenReturn(views);

    var result = service.getById(MODULE_FOO_ID, applicationId);

    assertThat(result.getModule().getModuleId()).isEqualTo(MODULE_FOO_ID);
    assertThat(result.getRequiredModules())
      .extracting(org.folio.am.domain.dto.ModuleBootstrapDiscovery::getModuleId)
      .containsExactly(MODULE_BAR_ID);

    verify(applicationClosureResolver).resolve(Set.of(applicationId));
    verify(repository).findAllRequiredByModuleIdAndApplicationIds(MODULE_FOO_ID, closure);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void getById_withoutApplicationId_fallsBackToLegacyQuery() {
    var moduleView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(List.of(moduleView)));

    var result = service.getById(MODULE_FOO_ID, null);

    assertThat(result.getModule().getModuleId()).isEqualTo(MODULE_FOO_ID);
    verify(repository).findAllRequiredByModuleId(MODULE_FOO_ID);
    verifyNoInteractions(applicationClosureResolver);
  }

  @Test
  void getById_withEmptyApplicationId_fallsBackToLegacyQuery() {
    var moduleView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(List.of(moduleView)));

    var result = service.getById(MODULE_FOO_ID, "");

    assertThat(result.getModule().getModuleId()).isEqualTo(MODULE_FOO_ID);
    verify(repository).findAllRequiredByModuleId(MODULE_FOO_ID);
    verifyNoInteractions(applicationClosureResolver);
  }

  @Test
  void getById_withBlankApplicationId_fallsBackToLegacyQuery() {
    var moduleView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);
    moduleView.setApplicationId("some-app-1.0.0");
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(List.of(moduleView)));

    var result = service.getById(MODULE_FOO_ID, "   ");

    assertThat(result.getModule().getModuleId()).isEqualTo(MODULE_FOO_ID);
    verify(repository).findAllRequiredByModuleId(MODULE_FOO_ID);
    verifyNoInteractions(applicationClosureResolver);
  }

  @Test
  void getById_withApplicationId_emptyClosureFallsBackToLegacyQuery() {
    var applicationId = "app-platform-minimal-2.0.53";
    var moduleView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);

    when(applicationClosureResolver.resolve(Set.of(applicationId))).thenReturn(Set.of());
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(new ArrayList<>(List.of(moduleView)));

    var result = service.getById(MODULE_FOO_ID, applicationId);

    assertThat(result.getModule().getModuleId()).isEqualTo(MODULE_FOO_ID);
    verify(applicationClosureResolver).resolve(Set.of(applicationId));
    verify(repository).findAllRequiredByModuleId(MODULE_FOO_ID);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void getById_withApplicationId_prefersSameApplicationModuleOverHigherVersion() {
    var applicationId = "app-platform-minimal-2.0.53";
    var otherAppId = "app-platform-complete-1.0.0";
    var closure = Set.of(applicationId, otherAppId);

    when(applicationClosureResolver.resolve(Set.of(applicationId))).thenReturn(closure);

    // mod-users-keycloak-3.0.13 is the module being bootstrapped (in applicationId)
    var keycloakView = moduleBootstrapView("mod-users-keycloak-3.0.13", "login");
    keycloakView.setApplicationId(applicationId);
    keycloakView.getDescriptor().addRequiresItem(new InterfaceReference().id("users"));
    keycloakView.setLocation(null);

    // mod-users-19.5.4 is in the preferred application (applicationId)
    var users54View = moduleBootstrapView("mod-users-19.5.4", "users");
    users54View.setApplicationId(applicationId);
    users54View.setLocation("http://5-4");

    // mod-users-19.6.0 is in the other application (higher version but not preferred)
    var users60View = moduleBootstrapView("mod-users-19.6.0", "users");
    users60View.setApplicationId(otherAppId);
    users60View.setLocation("http://6-0");

    when(repository.findAllRequiredByModuleIdAndApplicationIds(
      "mod-users-keycloak-3.0.13", closure))
      .thenReturn(new ArrayList<>(List.of(keycloakView, users54View, users60View)));

    var result = service.getById("mod-users-keycloak-3.0.13", applicationId);

    assertThat(result.getRequiredModules())
      .extracting(org.folio.am.domain.dto.ModuleBootstrapDiscovery::getModuleId)
      .containsExactly("mod-users-19.5.4");
  }
}
