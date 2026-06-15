package org.folio.am.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.folio.am.config.properties.BootstrapCacheProperties;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
@EnableConfigurationProperties(BootstrapCacheProperties.class)
public class BootstrapCacheConfiguration {

  public static final String MODULE_BOOTSTRAP_CACHE = "module-bootstrap";

  @Bean
  @ConditionalOnFarModeDisabled
  @ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
  public CacheManager bootstrapCacheManager(BootstrapCacheProperties properties) {
    var cacheManager = new CaffeineCacheManager(MODULE_BOOTSTRAP_CACHE);
    cacheManager.setCaffeine(Caffeine.newBuilder()
      .maximumSize(properties.getMaxSize())
      .expireAfterWrite(properties.getTtl()));
    return cacheManager;
  }

  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public CacheManager noOpCacheManager() {
    return new NoOpCacheManager();
  }
}
