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
  void onDiscoveryEvent_withDependents_evictsCapturedSet() {
    // delete events carry the fan-out captured inside the delete transaction -> evict that set directly
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0", List.of("mod-consumer-1.0.0")));
    verify(evictor).evictForModuleWithDependents("mod-x-1.0.0", List.of("mod-consumer-1.0.0"));
  }

  @Test
  void onDiscoveryEvent_withEmptyDependents_takesDeleteBranch_notRecompute() {
    // a delete of a provider with no dependents carries a non-null EMPTY list; it must still take the
    // captured-set (delete) branch, not the recompute (create/update) branch
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0", List.of()));
    verify(evictor).evictForModuleWithDependents("mod-x-1.0.0", List.of());
  }

  @Test
  void onDiscoveryEvent_nullEvent_delegatesNullToEvictor() {
    listener.onDiscoveryEvent(null);
    verify(evictor).evictForModule(null);
  }
}
