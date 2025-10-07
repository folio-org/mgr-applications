package org.folio.am.integration.kafka;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;
import org.folio.am.integration.kafka.model.TenantEntitlementEvent;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementEventListenerTest {

  private static final String MODULE_ID = "test-module-1.0.0";
  private static final String TENANT_NAME = "test-tenant";
  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private KongGatewayService kongGatewayService;

  @InjectMocks private EntitlementEventListener listener;

  @Test
  void onEntitlementEvent_positive_entitleEvent() {
    var event = entitlementEvent(TenantEntitlementEvent.Type.ENTITLE);

    listener.onEntitlementEvent(event);

    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
    verifyNoMoreInteractions(kongGatewayService);
  }

  @Test
  void onEntitlementEvent_positive_upgradeEvent() {
    var event = entitlementEvent(TenantEntitlementEvent.Type.UPGRADE);

    listener.onEntitlementEvent(event);

    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
    verifyNoMoreInteractions(kongGatewayService);
  }

  @Test
  void onEntitlementEvent_positive_revokeEvent() {
    var event = entitlementEvent(TenantEntitlementEvent.Type.REVOKE);

    listener.onEntitlementEvent(event);

    verify(kongGatewayService).removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);
    verifyNoMoreInteractions(kongGatewayService);
  }

  @Test
  void onEntitlementEvent_positive_nullEventType() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_ID, null);

    listener.onEntitlementEvent(event);

    verifyNoInteractions(kongGatewayService);
  }

  private static TenantEntitlementEvent entitlementEvent(TenantEntitlementEvent.Type type) {
    return TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_ID, type);
  }
}
