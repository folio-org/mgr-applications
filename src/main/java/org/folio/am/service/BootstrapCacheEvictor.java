package org.folio.am.service;

import lombok.extern.log4j.Log4j2;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Single point of full-flush invalidation for the module-bootstrap cache, shared by the in-process
 * and Kafka invalidators. Lives in its own bean so the {@code @CacheEvict} proxy is honored. Full
 * flush (not per-module) because a discovery change to module B can affect any module that depends
 * on an interface B provides (fan-out). No-op when the active cache manager is the {@code NoOp} one.
 */
@Log4j2
@Component
public class BootstrapCacheEvictor {

  @CacheEvict(cacheNames = BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE, allEntries = true)
  public void evictAll() {
    log.debug("Evicting all module-bootstrap cache entries");
  }
}
