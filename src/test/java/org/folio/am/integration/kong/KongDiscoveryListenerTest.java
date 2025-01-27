package org.folio.am.integration.kong;

import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.MODULE_URL;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.support.TestConstants.UPDATED_URL;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.repository.ModuleRepository;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.model.Service;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongDiscoveryListenerTest {

  @InjectMocks private KongDiscoveryListener service;
  @Mock private KongGatewayService kongAdminClient;
  @Mock private ModuleRepository moduleRepository;

  @Test
  void onCreateDiscovery_positive() {
    var mockModule = mock(ModuleEntity.class);
    when(moduleRepository.findById(any())).thenReturn(Optional.of(mockModule));
    var mockModuleDescriptor = mock(ModuleDescriptor.class);
    when(mockModule.getDescriptor()).thenReturn(mockModuleDescriptor);
    var kongService = kongService(MODULE_URL);
    service.onDiscoveryCreate(moduleDiscovery(), null);
    verify(kongAdminClient).upsertService(kongService);
    verify(kongAdminClient).addRoutes(null, Collections.singletonList(mockModuleDescriptor));
  }

  @Test
  void onUpdateDiscovery_positive() {
    var mockModule = mock(ModuleEntity.class);
    when(moduleRepository.findById(any())).thenReturn(Optional.of(mockModule));
    var mockModuleDescriptor = mock(ModuleDescriptor.class);
    when(mockModule.getDescriptor()).thenReturn(mockModuleDescriptor);
    var updatedService = kongService(UPDATED_URL);
    service.onDiscoveryUpdate(moduleDiscovery(SERVICE_NAME, SERVICE_VERSION, UPDATED_URL), null);
    verify(kongAdminClient).upsertService(updatedService);
    verify(kongAdminClient).deleteServiceRoutes(MODULE_ID);
    verify(kongAdminClient).addRoutes(null, Collections.singletonList(mockModuleDescriptor));
  }

  @Test
  void onDeleteDiscovery_positive() {
    doNothing().when(kongAdminClient).deleteService(MODULE_ID);
    service.onDiscoveryDelete(MODULE_ID, null, null);
    verify(kongAdminClient).deleteServiceRoutes(MODULE_ID);
    verify(kongAdminClient).deleteService(MODULE_ID);
  }

  private static Service kongService(String url) {
    return new Service().name(MODULE_ID).url(url);
  }
}
