package org.folio.am.integration.kong;

import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.MODULE_URL;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.support.TestConstants.UPDATED_URL;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import org.folio.am.integration.kong.model.KongService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongGatewayServiceTest {

  @InjectMocks private KongGatewayService service;

  @Mock private KongAdminClient kongAdminClient;

  @Test
  void onCreateDiscovery_positive() {
    var kongService = kongService(MODULE_URL);
    when(kongAdminClient.getService(MODULE_ID)).thenThrow(FeignException.NotFound.class);
    when(kongAdminClient.upsertService(MODULE_ID, kongService)).thenReturn(kongService);

    service.onDiscoveryCreate(moduleDiscovery(), null);

    verify(kongAdminClient).upsertService(MODULE_ID, kongService);
  }

  @Test
  void onCreateDiscovery_negative_serviceAlreadyExist() {
    var kongService = kongService(MODULE_URL);
    when(kongAdminClient.getService(MODULE_ID)).thenReturn(kongService);

    service.onDiscoveryCreate(moduleDiscovery(), null);

    verify(kongAdminClient, never()).upsertService(MODULE_ID, kongService);
  }

  @Test
  void onUpdateDiscovery_positive() {
    var kongService = kongService(MODULE_URL);
    var updatedService = kongService(UPDATED_URL);

    when(kongAdminClient.getService(MODULE_ID)).thenReturn(kongService);

    service.onDiscoveryUpdate(moduleDiscovery(SERVICE_NAME, SERVICE_VERSION, UPDATED_URL), null);

    verify(kongAdminClient).upsertService(MODULE_ID, updatedService);
  }

  @Test
  void onUpdateDiscovery_negative_discoveryNotChanged() {
    var kongService = kongService(MODULE_URL);
    when(kongAdminClient.getService(MODULE_ID)).thenReturn(kongService);

    service.onDiscoveryUpdate(moduleDiscovery(), null);

    verify(kongAdminClient, never()).upsertService(MODULE_ID, kongService);
  }

  @Test
  void onDeleteDiscovery_positive() {
    doNothing().when(kongAdminClient).deleteService(MODULE_ID);
    service.onDiscoveryDelete(MODULE_ID, null, null);
    verify(kongAdminClient).deleteService(MODULE_ID);
  }

  private static KongService kongService(String url) {
    return new KongService().name(MODULE_ID).url(url);
  }
}
