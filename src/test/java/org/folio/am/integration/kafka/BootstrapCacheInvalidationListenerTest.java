package org.folio.am.integration.kafka;

import static org.mockito.Mockito.verify;

import java.util.List;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.service.BootstrapCacheEvictor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BootstrapCacheInvalidationListenerTest {

  @Mock private BootstrapCacheEvictor evictor;
  @InjectMocks private BootstrapCacheInvalidationListener listener;

  @Test
  void onDiscoveryEvent_noDependents_recomputesFanOut() {
    // create/update events carry no dependents -> the provider's PROVIDES rows still exist, so re-derive the fan-out
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0"));
    verify(evictor).evictForModule("mod-x-1.0.0");
  }

  @Test
  void onDiscoveryEvent_withDependents_evictsCapturedSetWithoutRecomputing() {
    // delete events carry the dependents captured before deletion -> evict them directly (recompute would be empty)
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0", List.of("mod-consumer-1.0.0")));
    verify(evictor).evictForModuleWithDependents("mod-x-1.0.0", List.of("mod-consumer-1.0.0"));
  }

  @Test
  void onDiscoveryEvent_nullEvent_delegatesNullToEvictor() {
    listener.onDiscoveryEvent(null);
    verify(evictor).evictForModule(null);
  }
}
