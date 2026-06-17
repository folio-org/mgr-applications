package org.folio.am.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.integration.messaging.MessagePublisher;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.am.service.ApplicationDiscoveryListener;

@Log4j2
@RequiredArgsConstructor
public class DiscoveryPublisher implements ApplicationDiscoveryListener {

  public static final String DISCOVERY_DESTINATION = "discovery";

  private final MessagePublisher<DiscoveryEvent> messagePublisher;
  private final ModuleBootstrapRepository moduleBootstrapRepository;

  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    sendMessage(new DiscoveryEvent(moduleDiscovery.getId()));
  }

  @Override
  public void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    sendMessage(new DiscoveryEvent(moduleDiscovery.getId()));
  }

  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {
    // Capture the provider fan-out now, while this listener runs synchronously inside the delete transaction (the
    // module's PROVIDES rows still exist). The event is dispatched post-commit via the outbox, so every replica can
    // evict the dependents — re-deriving them on the consumer side after deletion would yield nothing.
    var dependentModuleIds = moduleBootstrapRepository.findAllDependentModuleIds(serviceId);
    sendMessage(new DiscoveryEvent(serviceId, dependentModuleIds));
  }

  private void sendMessage(DiscoveryEvent event) {
    log.debug("Sending discovery event: {}", event);
    messagePublisher.send(DISCOVERY_DESTINATION, event);
  }
}
