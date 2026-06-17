package org.folio.am.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Single point of module-bootstrap cache invalidation, shared by the in-process and Kafka
 * invalidators. A discovery change to module X invalidates only the snapshots that can actually
 * change: X's own snapshot plus every module that requires/optionally-uses an interface X provides
 * (the provider fan-out resolved via {@link ModuleBootstrapRepository#findAllDependentModuleIds}).
 * Eviction is programmatic (not {@code @CacheEvict}) so a dynamic set of keys can be evicted in one
 * pass. No-op when the active cache manager is the {@code NoOp} one.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class BootstrapCacheEvictor {

  private final CacheManager cacheManager;
  private final ModuleBootstrapRepository repository;

  /**
   * Evicts the cached bootstrap snapshot for {@code moduleId} and for every module whose
   * required/optional interface is provided by {@code moduleId}. The reverse-dependency lookup runs
   * in the repository's own read-only transaction, so this is safe to call after the discovery-change
   * commit (in-process path) or from the Kafka consumer thread (no ambient transaction).
   *
   * @param moduleId id of the module whose discovery changed
   */
  public void evictForModule(String moduleId) {
    if (moduleId == null) {
      log.warn("Skipping module-bootstrap cache eviction: null moduleId");
      return;
    }
    evictKeys(moduleId, repository.findAllDependentModuleIds(moduleId));
  }

  /**
   * Evicts {@code moduleId} plus an already-resolved dependent set, without re-deriving the dependents from the
   * repository. Used by the delete path, where the dependents are captured before the module's {@code PROVIDES}
   * rows are removed — re-deriving them post-commit (as {@link #evictForModule}) would return nothing and leave
   * dependents in other applications stale until the TTL.
   *
   * @param moduleId id of the deleted module (its own snapshot is evicted too)
   * @param dependentModuleIds dependents captured before deletion
   */
  public void evictForModuleWithDependents(String moduleId, Collection<String> dependentModuleIds) {
    evictKeys(moduleId, dependentModuleIds);
  }

  /**
   * Resolves the dependent module ids of {@code moduleId} (modules whose required/optional interface is provided by
   * it). Call this while the provider's {@code PROVIDES} rows still exist (e.g. before a delete commits) so the set
   * can be evicted post-commit.
   *
   * @param moduleId the module whose dependents to resolve
   * @return dependent module ids, or an empty list when {@code moduleId} is null
   */
  public List<String> findDependentModuleIds(String moduleId) {
    return moduleId == null ? List.of() : repository.findAllDependentModuleIds(moduleId);
  }

  private void evictKeys(String moduleId, Collection<String> dependentModuleIds) {
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    if (cache == null) {
      return;
    }
    var keys = new LinkedHashSet<String>();
    if (moduleId != null) {
      keys.add(moduleId);
    }
    if (dependentModuleIds != null) {
      keys.addAll(dependentModuleIds);
    }
    if (keys.isEmpty()) {
      return;
    }
    log.debug("Evicting module-bootstrap cache entries [changedModuleId={}, keys={}]", moduleId, keys);
    keys.forEach(cache::evict);
  }
}
