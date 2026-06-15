package org.folio.am.config.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.bootstrap-cache")
public class BootstrapCacheProperties {

  /**
   * Maximum number of cached module-bootstrap snapshots (Caffeine maximumSize).
   */
  private long maxSize = 1000;

  /**
   * Memory-bound / missed-event backstop expiry (Caffeine expireAfterWrite). Not the freshness
   * mechanism — invalidation is event-driven.
   */
  private Duration ttl = Duration.ofMinutes(30);
}
