package org.folio.am.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts the module-bootstrap cache on the writing replica immediately after the discovery-change
 * transaction commits, tightening that replica's staleness window to ~zero (other replicas converge
 * via the Kafka broadcast). {@code afterCommit} avoids an in-transaction race where a concurrent read
 * could repopulate the cache with about-to-be-stale data. Active only when the cache is enabled and
 * FAR mode is off.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheInProcessInvalidator implements ApplicationDiscoveryListener {

  private final BootstrapCacheEvictor evictor;

  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    evictAfterCommit(moduleDiscovery.getId());
  }

  @Override
  public void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    evictAfterCommit(moduleDiscovery.getId());
  }

  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {
    evictAfterCommit(serviceId);
  }

  private void evictAfterCommit(String moduleId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      evictor.evictForModule(moduleId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        log.debug("Invalidating module-bootstrap cache after discovery change commit [moduleId={}]", moduleId);
        evictor.evictForModule(moduleId);
      }
    });
  }
}
