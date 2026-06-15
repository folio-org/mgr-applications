package org.folio.am.integration.kafka;

import static org.mockito.Mockito.verify;

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
  void onDiscoveryEvent_evictsAll() {
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0")); // @AllArgsConstructor: single String arg
    verify(evictor).evictAll();
  }
}
