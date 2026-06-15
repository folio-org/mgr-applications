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

  /**
   * Base name of the per-instance Kafka consumer group used for broadcast cache invalidation. A
   * random UUID is appended so every replica forms its own group and receives every event. Override
   * via {@code KAFKA_BOOTSTRAP_CACHE_GROUP_ID}; the default is environment-prefixed in config.
   */
  private String groupIdPrefix = "mgr-applications-bootstrap-cache";
}
