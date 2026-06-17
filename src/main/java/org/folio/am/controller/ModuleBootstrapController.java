package org.folio.am.controller;

import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.EgressBootstrap;
import org.folio.am.domain.dto.EgressBootstrapRequest;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.rest.resource.ModuleBootstrapApi;
import org.folio.am.service.ModuleBootstrapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModuleBootstrapController extends BaseController implements ModuleBootstrapApi {

  private final ModuleBootstrapService service;

  @Override
  public ResponseEntity<ModuleBootstrap> getModuleBootstrap(String id) {
    var moduleBootstrap = service.getById(id);

    return ResponseEntity.ok(moduleBootstrap);
  }

  @Override
  public ResponseEntity<ModuleBootstrap> getModuleIngressBootstrap(String id) {
    return ResponseEntity.ok(service.getIngressBootstrap(id));
  }

  @Override
  public ResponseEntity<EgressBootstrap> getModuleEgressBootstrap(String id, EgressBootstrapRequest request) {
    return ResponseEntity.ok(service.getEgressBootstrap(id, request.getApplicationIds()));
  }
}
