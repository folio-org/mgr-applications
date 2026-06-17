package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@SpringBootTest(classes = {BootstrapCacheConfiguration.class, ModuleBootstrapDataProvider.class})
@TestPropertySource(properties = "application.bootstrap-cache.enabled=true")
class ModuleBootstrapDataProviderCacheTest {

  @Autowired private ModuleBootstrapDataProvider provider;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private ModuleBootstrapRepository repository;

  @BeforeEach
  void clearCache() {
    // the cache bean is a singleton shared across test methods; isolate each test from the others
    cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE).clear();
  }

  @Test
  void getData_cachesByModuleId_andRefetchesAfterEvict() {
    when(repository.findAllRequiredByModuleId("mod-foo-1.0.0"))
      .thenReturn(List.of(moduleBootstrapView("mod-foo-1.0.0", "foo-int")));

    provider.getData("mod-foo-1.0.0");
    provider.getData("mod-foo-1.0.0");
    verify(repository, times(1)).findAllRequiredByModuleId("mod-foo-1.0.0");

    cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE).clear();

    provider.getData("mod-foo-1.0.0");
    verify(repository, times(2)).findAllRequiredByModuleId("mod-foo-1.0.0");
    assertThat(cacheManager.getCacheNames()).contains(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
  }

  @Test
  void getData_cacheKeyDiscriminatesByModuleId() {
    when(repository.findAllRequiredByModuleId("mod-foo-1.0.0"))
      .thenReturn(List.of(moduleBootstrapView("mod-foo-1.0.0", "foo-int")));
    when(repository.findAllRequiredByModuleId("mod-bar-1.0.0"))
      .thenReturn(List.of(moduleBootstrapView("mod-bar-1.0.0", "bar-int")));

    var foo = provider.getData("mod-foo-1.0.0");
    var bar = provider.getData("mod-bar-1.0.0");
    assertThat(foo.self().id()).isEqualTo("mod-foo-1.0.0");
    assertThat(bar.self().id()).isEqualTo("mod-bar-1.0.0");

    // second round must be served from distinct cache entries, not a single shared (constant-key) entry
    provider.getData("mod-foo-1.0.0");
    provider.getData("mod-bar-1.0.0");
    verify(repository, times(1)).findAllRequiredByModuleId("mod-foo-1.0.0");
    verify(repository, times(1)).findAllRequiredByModuleId("mod-bar-1.0.0");
  }
}
