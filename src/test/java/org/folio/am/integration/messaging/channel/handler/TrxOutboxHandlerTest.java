package org.folio.am.integration.messaging.channel.handler;

import static org.folio.am.integration.messaging.MessagingTestValues.genericMessage;
import static org.mockito.Mockito.doNothing;

import org.folio.am.integration.messaging.outbox.store.TrxOutboxStore;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TrxOutboxHandlerTest {

  @Mock private TrxOutboxStore store;
  @InjectMocks private TrxOutboxHandler handler;

  @Test
  void handleMessage_positive() {
    var msg = genericMessage();

    doNothing().when(store).saveMessage(msg);

    handler.handleMessage(msg);
  }
}
