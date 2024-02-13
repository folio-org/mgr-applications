package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_INTERFACE_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_INTERFACE_ID;
import static org.folio.am.support.TestValues.moduleBootstrap;
import static org.folio.am.support.TestValues.moduleBootstrapDiscovery;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.folio.am.mapper.ModuleBootstrapMapperImpl;
import org.folio.am.repository.ModuleBootstrapViewRepository;
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

  @Mock private ModuleBootstrapViewRepository repository;

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
    var expectedViews = new ArrayList<>(Arrays.asList(expectedView, expectedDependencyView));

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
    var expectedViews = new ArrayList<>(Arrays.asList(expectedView, expectedDependencyView));

    var expectedModuleDiscovery = moduleBootstrapDiscovery(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);

    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(expectedViews);

    var actual = service.getById(MODULE_FOO_ID);
    assertThat(actual).isEqualTo(moduleBootstrap(expectedModuleDiscovery));
  }

  @Test
  void getById_negative_notFound() {
    when(repository.findAllRequiredByModuleId(MODULE_FOO_ID)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.getById(MODULE_FOO_ID))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + MODULE_FOO_ID);
  }
}
