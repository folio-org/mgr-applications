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
    // Capture the provider fan-out while this listener runs inside the delete transaction and ship it in the event.
    // A discovery delete today only nulls the module's discovery_url — its PROVIDES rows survive — so a consumer
    // could re-derive the dependents itself; capturing them here is defensive: it keeps the broadcast correct even
    // if delete semantics later remove the module/interface rows, and spares each replica the reverse-dependency
    // query.
    var dependentModuleIds = moduleBootstrapRepository.findAllDependentModuleIds(serviceId);
    sendMessage(new DiscoveryEvent(serviceId, dependentModuleIds));
  }

  private void sendMessage(DiscoveryEvent event) {
    log.debug("Sending discovery event: {}", event);
    messagePublisher.send(DISCOVERY_DESTINATION, event);
  }
}
