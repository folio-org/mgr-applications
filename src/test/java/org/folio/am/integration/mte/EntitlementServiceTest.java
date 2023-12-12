package org.folio.am.integration.mte;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.test.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.model.ResultList;
import org.folio.am.exception.ServiceException;
import org.folio.am.integration.mte.model.Entitlement;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

  private static final String QUERY = "applicationId=test-app-1.0.0";

  @Mock private TenantEntitlementClient tenantEntitlementClient;
  @InjectMocks private EntitlementService entitlementService;

  @Test
  void getTenants_true() {
    var entitlementList = ResultList
      .of(1, List.of(Entitlement.of(APPLICATION_ID, TENANT_ID)));

    when(tenantEntitlementClient.findByQuery(QUERY, OKAPI_AUTH_TOKEN)).thenReturn(entitlementList);
    assertFalse(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN).isEmpty());
  }

  @Test
  void getTenants_false() {
    var entitlementList =  ResultList.<Entitlement>empty();

    when(tenantEntitlementClient.findByQuery(QUERY, OKAPI_AUTH_TOKEN)).thenReturn(entitlementList);

    assertTrue(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN).isEmpty());
  }

  @Test
  void getTenants_negative_serviceException() {
    when(tenantEntitlementClient.findByQuery(QUERY, OKAPI_AUTH_TOKEN)).thenThrow(ServiceException.class);

    assertThatThrownBy(() -> entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN))
      .isInstanceOf(ServiceException.class);
  }
}
