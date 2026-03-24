package org.folio.am.integration.mte;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import org.folio.am.integration.mte.model.Entitlement;
import org.folio.common.domain.model.ResultList;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface TenantEntitlementClient {

  @GetExchange("/entitlements")
  ResultList<Entitlement> findByQuery(@RequestParam(value = "query", required = false) String query,
                                      @RequestHeader(TOKEN) String token);
}
