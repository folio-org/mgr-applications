package org.folio.am.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

@UnitTest
class BootstrapCacheConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(BootstrapCacheConfiguration.class);

  @Test
  void cacheManager_isCaffeine_whenEnabledAndFarModeOff() {
    runner.run(context -> {
      assertThat(context).hasSingleBean(CacheManager.class);
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class);
    });
  }

  @Test
  void cacheManager_isNoOp_whenFarModeOn() {
    runner.withPropertyValues("application.far-mode.enabled=true").run(context ->
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
  }

  @Test
  void cacheManager_isNoOp_whenDisabled() {
    runner.withPropertyValues("application.bootstrap-cache.enabled=false").run(context ->
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
  }
}
