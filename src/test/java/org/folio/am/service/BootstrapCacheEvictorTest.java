package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

@UnitTest
@SpringBootTest(classes = {BootstrapCacheConfiguration.class, BootstrapCacheEvictor.class})
@TestPropertySource(properties = "application.bootstrap-cache.enabled=true")
class BootstrapCacheEvictorTest {

  @Autowired private BootstrapCacheEvictor evictor;
  @Autowired private CacheManager cacheManager;

  @Test
  void evictAll_clearsAllEntries() {
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    cache.put("k", "v");

    evictor.evictAll();

    assertThat(cache.get("k")).isNull();
  }
}
