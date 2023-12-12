package org.folio.am.integration.mte;

import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.folio.am.domain.model.ResultList;
import org.folio.am.integration.mte.model.Entitlement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

public interface TenantEntitlementClient {

  @GetMapping(value = "/entitlements", consumes = APPLICATION_JSON_VALUE)
  ResultList<Entitlement> findByQuery(@RequestParam(value = "query", required = false) String query,
                                      @RequestHeader(TOKEN) String token);
}
