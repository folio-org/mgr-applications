package org.folio.am.integration.kafka;

import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.integration.messaging.MessagePublisher;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaDiscoveryPublisherTest {

  @Mock private MessagePublisher<DiscoveryEvent> messagePublisher;

  @InjectMocks private DiscoveryPublisher service;

  @Test
  void onDiscoveryCreate_positive() {
    service.onDiscoveryCreate(moduleDiscovery(), "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION, new DiscoveryEvent(MODULE_ID));
    verifyNoMoreInteractions(messagePublisher);
  }

  @Test
  void onDiscoveryUpdate_positive() {
    service.onDiscoveryUpdate(moduleDiscovery(), "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION, new DiscoveryEvent(MODULE_ID));
    verifyNoMoreInteractions(messagePublisher);
  }

  @Test
  void onDiscoveryDelete_positive() {
    service.onDiscoveryDelete(MODULE_ID, MODULE_ID, "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION, new DiscoveryEvent(MODULE_ID));
    verifyNoMoreInteractions(messagePublisher);
  }
}
