package org.folio.am.controller;

import static org.springframework.http.HttpStatus.CREATED;

import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.rest.resource.ModuleDiscoveryApi;
import org.folio.am.service.ModuleDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModuleDiscoveryController extends BaseController implements ModuleDiscoveryApi {

  private final ModuleDiscoveryService moduleDiscoveryService;

  @Override
  public ResponseEntity<ModuleDiscovery> getModuleDiscovery(String moduleId) {
    return ResponseEntity.ok(moduleDiscoveryService.get(moduleId));
  }

  @Override
  public ResponseEntity<ModuleDiscoveries> searchModuleDiscovery(String query, Integer offset, Integer limit) {
    var moduleDiscoveries = moduleDiscoveryService.search(query, limit, offset);
    return ResponseEntity.ok(moduleDiscoveries);
  }

  @Override
  public ResponseEntity<ModuleDiscovery> createModuleDiscovery(String id, ModuleDiscovery discovery, String token) {
    var moduleDiscovery = moduleDiscoveryService.create(id, discovery, token);
    return ResponseEntity.status(CREATED).body(moduleDiscovery);
  }

  @Override
  public ResponseEntity<ModuleDiscoveries> createModuleDiscoveries(String token, ModuleDiscoveries discoveries) {
    var moduleDiscoveries = moduleDiscoveryService.create(discoveries, token);
    return ResponseEntity.status(CREATED).body(moduleDiscoveries);
  }

  @Override
  public ResponseEntity<Void> updateModuleDiscovery(String id, ModuleDiscovery discovery, String token) {
    moduleDiscoveryService.update(id, discovery, token);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteModuleDiscovery(String moduleId, String token) {
    moduleDiscoveryService.delete(moduleId, token);
    return ResponseEntity.noContent().build();
  }
}
