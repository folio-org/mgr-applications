package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@SpringBootTest(classes = {BootstrapCacheConfiguration.class, BootstrapCacheEvictor.class})
@TestPropertySource(properties = "application.bootstrap-cache.enabled=true")
class BootstrapCacheEvictorTest {

  @Autowired private BootstrapCacheEvictor evictor;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private ModuleBootstrapRepository repository;

  @Test
  void evictForModule_evictsChangedModuleAndDependents_keepingUnrelated() {
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    assertThat(cache).isNotNull();

    cache.put("mod-provider-1.0.0", "provider-snapshot");
    cache.put("mod-consumer-1.0.0", "consumer-snapshot");
    cache.put("mod-unrelated-1.0.0", "unrelated-snapshot");
    when(repository.findAllDependentModuleIds("mod-provider-1.0.0"))
      .thenReturn(List.of("mod-consumer-1.0.0"));

    evictor.evictForModule("mod-provider-1.0.0");

    assertThat(cache.get("mod-provider-1.0.0")).isNull();      // the changed module itself
    assertThat(cache.get("mod-consumer-1.0.0")).isNull();      // dependent (provider fan-out)
    assertThat(cache.get("mod-unrelated-1.0.0")).isNotNull();  // untouched -> scoped, not a full flush
  }

  @Test
  void evictForModuleWithDependents_evictsModuleAndGivenDependents_withoutQueryingRepository() {
    // Delete path: dependents are captured before the module's PROVIDES rows are removed, so the evictor
    // must evict the supplied set directly and never re-derive it from the repository (which would be empty).
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    assertThat(cache).isNotNull();

    cache.put("mod-provider-1.0.0", "provider-snapshot");
    cache.put("mod-consumer-1.0.0", "consumer-snapshot");
    cache.put("mod-unrelated-1.0.0", "unrelated-snapshot");

    evictor.evictForModuleWithDependents("mod-provider-1.0.0", List.of("mod-consumer-1.0.0"));

    assertThat(cache.get("mod-provider-1.0.0")).isNull();      // the deleted module itself
    assertThat(cache.get("mod-consumer-1.0.0")).isNull();      // dependent captured before deletion
    assertThat(cache.get("mod-unrelated-1.0.0")).isNotNull();  // untouched -> scoped, not a full flush
    verify(repository, never()).findAllDependentModuleIds(any());
  }

  @Test
  void findDependentModuleIds_delegatesToRepository() {
    when(repository.findAllDependentModuleIds("mod-provider-1.0.0"))
      .thenReturn(List.of("mod-consumer-1.0.0"));

    assertThat(evictor.findDependentModuleIds("mod-provider-1.0.0")).containsExactly("mod-consumer-1.0.0");
  }

  @Test
  void findDependentModuleIds_nullModuleId_returnsEmptyWithoutQueryingRepository() {
    assertThat(evictor.findDependentModuleIds(null)).isEmpty();
    verify(repository, never()).findAllDependentModuleIds(any());
  }

  @Test
  void evictForModule_nullModuleId_isNoOp() {
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    assertThat(cache).isNotNull();

    cache.put("mod-x-1.0.0", "snapshot");

    evictor.evictForModule(null);

    assertThat(cache.get("mod-x-1.0.0")).isNotNull();
  }
}
