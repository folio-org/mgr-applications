package org.folio.am.integration.mte;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.exception.ServiceException;
import org.folio.am.integration.mte.model.Entitlement;
import org.springframework.web.client.RestClientException;

@Log4j2
@RequiredArgsConstructor
public class EntitlementService {

  private final TenantEntitlementClient entitlementClient;

  public List<String> getTenants(String id, String authToken) {
    log.debug("Retrieving entitlement [appId: {}]", id);
    var query = getEntitlementQuery(id);

    try {
      var result = entitlementClient.findByQuery(query, authToken);
      if (isNotEmpty(result.getRecords())) {
        return result.getRecords()
          .stream().map(Entitlement::getTenantId)
          .toList();
      } else {
        return Collections.emptyList();
      }
    } catch (RestClientException cause) {
      throw new ServiceException("mgr-tenant-entitlements is not available", cause);
    }
  }

  private String getEntitlementQuery(String id) {
    return "applicationId=" + id;
  }
}
