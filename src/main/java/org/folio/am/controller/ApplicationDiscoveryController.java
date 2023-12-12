package org.folio.am.controller;

import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.rest.resource.ApplicationDiscoveryApi;
import org.folio.am.service.ApplicationDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationDiscoveryController implements ApplicationDiscoveryApi {

  private final ApplicationDiscoveryService service;

  @Override
  public ResponseEntity<ModuleDiscoveries> getDiscovery(String appId, Integer offset, Integer limit) {
    var moduleDiscoveries = service.get(appId, offset, limit);
    return ResponseEntity.ok(moduleDiscoveries);
  }
}
