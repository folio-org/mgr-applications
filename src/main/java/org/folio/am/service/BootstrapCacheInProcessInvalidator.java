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
    // Capture the dependent set now, inside the delete transaction, and evict it afterCommit. A discovery delete
    // currently only nulls the module's discovery_url — its PROVIDES rows survive — so re-deriving the dependents
    // afterCommit would still work; capturing up front is defensive and keeps eviction correct even if delete
    // semantics later remove those rows.
    var dependents = evictor.findDependentModuleIds(serviceId);
    runAfterCommit(serviceId, () -> evictor.evictForModuleWithDependents(serviceId, dependents));
  }

  private void evictAfterCommit(String moduleId) {
    runAfterCommit(moduleId, () -> evictor.evictForModule(moduleId));
  }

  private void runAfterCommit(String moduleId, Runnable eviction) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      eviction.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        log.debug("Invalidating module-bootstrap cache after discovery change commit [moduleId={}]", moduleId);
        eviction.run();
      }
    });
  }
}
