package org.folio.am.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.integration.messaging.MessagePublisher;
import org.folio.am.service.ApplicationDiscoveryListener;

@Log4j2
@RequiredArgsConstructor
public class DiscoveryPublisher implements ApplicationDiscoveryListener {

  public static final String DISCOVERY_DESTINATION = "discovery";

  private final MessagePublisher<DiscoveryEvent> messagePublisher;

  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, String token) {
    sendMessage(moduleDiscovery.getId());
  }

  @Override
  public void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, String token) {
    sendMessage(moduleDiscovery.getId());
  }

  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, String token) {
    sendMessage(serviceId);
  }

  private void sendMessage(String serviceId) {
    log.debug("Sending discovery event for module {}", serviceId);
    messagePublisher.send(DISCOVERY_DESTINATION, new DiscoveryEvent(serviceId));
  }
}
