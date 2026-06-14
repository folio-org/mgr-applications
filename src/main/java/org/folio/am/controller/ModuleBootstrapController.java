package org.folio.am.controller;

import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapRequest;
import org.folio.am.domain.dto.ModuleBootstrapResponse;
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
  public ResponseEntity<ModuleBootstrapResponse> postModuleBootstrap(String id, ModuleBootstrapRequest request) {
    var response = new ModuleBootstrapResponse();
    if (request.getType() == ModuleBootstrapRequest.TypeEnum.EGRESS) {
      response.egress(service.getEgressBootstraps(id, request.getTenants()));
      response.setIngress(null);
    } else {
      response.ingress(service.getIngressBootstrap(id));
      response.setEgress(null);
    }
    return ResponseEntity.ok(response);
  }
}
