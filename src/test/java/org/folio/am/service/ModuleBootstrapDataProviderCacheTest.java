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
}
