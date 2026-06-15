package org.folio.am.service;

import lombok.RequiredArgsConstructor;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single DB-touching, cached entry point for module bootstrap data. Lives in its own bean (not on
 * {@link ModuleBootstrapService}) so the cache proxy is honored — a self-invocation from the service
 * would bypass it.
 */
@Component
@RequiredArgsConstructor
public class ModuleBootstrapDataProvider {

  private final ModuleBootstrapRepository repository;

  @Cacheable(cacheNames = BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE, key = "#moduleId")
  @Transactional(readOnly = true)
  public ModuleBootstrapData getData(String moduleId) {
    var rows = repository.findAllRequiredByModuleId(moduleId);
    var appIdRows = repository.findApplicationIdsByModuleId(moduleId);
    return ModuleBootstrapData.from(moduleId, rows, appIdRows);
  }
}
