package org.folio.am.integration.kafka;

import static org.folio.am.integration.kafka.DiscoveryPublisher.DISCOVERY_DESTINATION;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.integration.messaging.MessagePublisher;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaDiscoveryPublisherTest {

  @Mock private MessagePublisher<DiscoveryEvent> messagePublisher;
  @Mock private ModuleBootstrapRepository moduleBootstrapRepository;

  @InjectMocks private DiscoveryPublisher service;

  @ParameterizedTest
  @EnumSource(ModuleType.class)
  void onDiscoveryCreate_positive(ModuleType moduleType) {
    service.onDiscoveryCreate(moduleDiscovery(), moduleType, "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION, new DiscoveryEvent(MODULE_ID));
    verify(moduleBootstrapRepository, never()).findAllDependentModuleIds(any());
    verifyNoMoreInteractions(messagePublisher);
  }

  @ParameterizedTest
  @EnumSource(ModuleType.class)
  void onDiscoveryUpdate_positive(ModuleType moduleType) {
    service.onDiscoveryUpdate(moduleDiscovery(), moduleType, "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION, new DiscoveryEvent(MODULE_ID));
    verify(moduleBootstrapRepository, never()).findAllDependentModuleIds(any());
    verifyNoMoreInteractions(messagePublisher);
  }

  @ParameterizedTest
  @EnumSource(ModuleType.class)
  void onDiscoveryDelete_capturesDependentsAndPublishesThem(ModuleType moduleType) {
    // dependents captured before deletion so every replica can evict them; re-deriving post-delete yields nothing
    when(moduleBootstrapRepository.findAllDependentModuleIds(MODULE_ID)).thenReturn(List.of("mod-consumer-1.0.0"));

    service.onDiscoveryDelete(MODULE_ID, MODULE_ID, moduleType, "test");

    verify(messagePublisher).send(DISCOVERY_DESTINATION,
      new DiscoveryEvent(MODULE_ID, List.of("mod-consumer-1.0.0")));
    verifyNoMoreInteractions(messagePublisher);
  }
}
