package org.folio.am.service;

import java.util.LinkedHashSet;
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
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    if (cache == null) {
      return;
    }
    var keys = new LinkedHashSet<String>();
    keys.add(moduleId);
    keys.addAll(repository.findAllDependentModuleIds(moduleId));
    log.debug("Evicting module-bootstrap cache entries [changedModuleId={}, keys={}]", moduleId, keys);
    keys.forEach(cache::evict);
  }
}
