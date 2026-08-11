package org.folio.am.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationEventPublisher {
  private final List<ApplicationDiscoveryListener> discoveryListeners;

  public void publishDiscoveryCreate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    log.info("Executing 'onDiscoveryCreate' handlers for service: id = {}", moduleDiscovery.getId());
    discoveryListeners.forEach(listener -> listener.onDiscoveryCreate(moduleDiscovery, type, token));
  }

  public void publishDiscoveryUpdate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    log.info("Executing 'onDiscoveryUpdate' handlers for service: id = {}", moduleDiscovery.getId());
    discoveryListeners.forEach(listener -> listener.onDiscoveryUpdate(moduleDiscovery, type, token));
  }

  public void publishDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {
    log.info("Executing 'onDiscoveryDelete' handlers for service: id = {}", serviceId);
    discoveryListeners.forEach(listener -> listener.onDiscoveryDelete(serviceId, instanceId, type, token));
  }
}
