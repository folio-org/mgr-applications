package org.folio.am.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.service.BootstrapCacheEvictor;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes discovery events from {@code {ENV}.discovery} (per-instance group, broadcast) and
 * full-flushes the module-bootstrap cache on this replica. Invalidation is idempotent, so outbox
 * replay / duplicate delivery is harmless.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheInvalidationListener {

  private final BootstrapCacheEvictor evictor;

  @KafkaListener(
    id = "bootstrap-cache-invalidation-listener",
    containerFactory = "bootstrapCacheKafkaListenerContainerFactory",
    topics = "${spring.kafka.topics.discovery}")
  public void onDiscoveryEvent(DiscoveryEvent event) {
    log.debug("Invalidating module-bootstrap cache on discovery event: moduleId = {}",
      event == null ? null : event.getModuleId());
    evictor.evictAll();
  }
}
