package org.folio.am.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.CREATED_HEADER;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.DESTINATION_HEADER;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.PRIMARY_ID_HEADER;
import static org.folio.am.integration.messaging.MessagingTestValues.DESTINATION;
import static org.folio.am.integration.messaging.MessagingTestValues.HEADER;
import static org.folio.am.integration.messaging.MessagingTestValues.PAYLOAD;
import static org.folio.am.integration.messaging.MessagingTestValues.VALUE;
import static org.folio.am.integration.messaging.MessagingTestValues.rawMessage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.folio.am.integration.messaging.MessagingTestValues.Payload;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.core.GenericMessagingTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class GenericMessagePublisherTest {

  @Mock private GenericMessagingTemplate messagingTemplate;
  @InjectMocks private GenericMessagePublisher<Payload> publisher;
  @Captor private ArgumentCaptor<Message<?>> msgCaptor;

  @Test
  void sendPayload_positive() {
    doNothing().when(messagingTemplate).send(anyString(), msgCaptor.capture());

    publisher.send(DESTINATION, PAYLOAD);

    verify(messagingTemplate).send(eq(DESTINATION), any(Message.class));

    var sendMsg = msgCaptor.getValue();
    assertThat(sendMsg.getPayload()).isEqualTo(PAYLOAD);
    var headers = sendMsg.getHeaders();
    assertThat(headers.get(PRIMARY_ID_HEADER, UUID.class)).isNotNull();
    assertThat(headers.get(CREATED_HEADER, Long.class)).isNotNull();
    assertThat(headers.get(DESTINATION_HEADER, String.class)).isEqualTo(DESTINATION);
  }

  @Test
  void sendMessage_positive() {
    doNothing().when(messagingTemplate).send(anyString(), msgCaptor.capture());

    var msg = rawMessage();
    publisher.send(DESTINATION, msg);

    verify(messagingTemplate).send(eq(DESTINATION), any(Message.class));

    var sendMsg = msgCaptor.getValue();
    assertThat(sendMsg.getPayload()).isEqualTo(PAYLOAD);
    var headers = sendMsg.getHeaders();
    assertThat(headers.get(HEADER)).isEqualTo(VALUE);
    assertThat(headers.get(PRIMARY_ID_HEADER, UUID.class)).isNotNull();
    assertThat(headers.get(CREATED_HEADER, Long.class)).isNotNull();
    assertThat(headers.get(DESTINATION_HEADER, String.class)).isEqualTo(DESTINATION);
  }
}
