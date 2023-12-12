package org.folio.am.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_INTERFACE_ID;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.am.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleBootstrapMapperTest {

  private final ModuleBootstrapMapper mapper = new ModuleBootstrapMapperImpl();

  @Test
  void convert_moduleBootstrapView_to_moduleBootstrapDiscovery_success() {
    var bootstrapView = moduleBootstrapView(MODULE_FOO_ID, MODULE_FOO_INTERFACE_ID);

    var bootstrapDiscovery = mapper.convert(bootstrapView);

    assertNotNull(bootstrapDiscovery);
    assertEquals(bootstrapView.getId(), bootstrapDiscovery.getModuleId());
    assertEquals(bootstrapView.getApplicationId(), bootstrapDiscovery.getApplicationId());
    assertEquals(bootstrapView.getLocation(), bootstrapDiscovery.getLocation());
    assertThat(bootstrapDiscovery.getInterfaces()).hasSize(bootstrapView.getDescriptor().getProvides().size());
  }

  @Test
  void convert_interfaceDescriptor_to_moduleBootstrapInterface_success() {
    var interfaceDescriptor = TestValues.interfaceDescriptor(MODULE_FOO_INTERFACE_ID);

    var bootstrapInterface = mapper.convert(interfaceDescriptor);

    assertNotNull(bootstrapInterface);
    assertEquals(interfaceDescriptor.getId(), bootstrapInterface.getId());
    assertEquals(interfaceDescriptor.getVersion(), bootstrapInterface.getVersion());
    assertEquals(interfaceDescriptor.getInterfaceType(), bootstrapInterface.getInterfaceType());
    assertEquals(interfaceDescriptor.getHandlers().size(), bootstrapInterface.getEndpoints().size());
  }

  @Test
  void convert_routingEntry_to_moduleBootstrapEndpoint_success() {
    var routingEntry = TestValues.routingEntry();

    var bootstrapEndpoint = mapper.convert(routingEntry);

    assertNotNull(bootstrapEndpoint);
    assertEquals(routingEntry.getPath(), bootstrapEndpoint.getPath());
    assertEquals(routingEntry.getPathPattern(), bootstrapEndpoint.getPathPattern());
    assertEquals(routingEntry.getPermissionsRequired(), bootstrapEndpoint.getPermissionsRequired());
    assertThat(bootstrapEndpoint.getMethods()).containsExactlyInAnyOrderElementsOf(routingEntry.getMethods());
  }
}
