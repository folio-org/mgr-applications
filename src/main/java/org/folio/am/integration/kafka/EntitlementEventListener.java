package org.folio.am.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.kafka.model.TenantEntitlementEvent;
import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "application.kong.tenant-checks.enabled", havingValue = "true")
public class EntitlementEventListener {

  private final KongGatewayService gatewayService;

  @KafkaListener(
    id = "entitlement-event-listener",
    containerFactory = "entitlementKafkaListenerContainerFactory",
    groupId = "${spring.kafka.consumer.group-id}",
    topicPattern = "${spring.kafka.topics.entitlement}")
  public void onEntitlementEvent(TenantEntitlementEvent event) {
    log.debug("Received entitlement event: {}", event);

    var moduleId = event.getModuleId();
    var tenant = event.getTenantName();
    var type = event.getType();

    if (type == null) {
      log.warn("Unsupported event type: null");
      return;
    }

    switch (type) {
      case ENTITLE, UPGRADE -> gatewayService.addTenantToModuleRoutes(moduleId, tenant);
      case REVOKE -> gatewayService.removeTenantFromModuleRoutes(moduleId, tenant);
      default -> log.warn("Unsupported event type: {}", type);
    }
  }
}

