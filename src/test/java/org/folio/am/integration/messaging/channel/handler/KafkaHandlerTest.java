package org.folio.am.integration.messaging.channel.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.integration.messaging.MessagingTestValues.DESTINATION;
import static org.folio.am.integration.messaging.MessagingTestValues.PAYLOAD;
import static org.folio.am.integration.messaging.MessagingTestValues.genericMessage;
import static org.folio.am.integration.messaging.MessagingTestValues.genericMessageWithDestination;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.mockito.Mockito.when;

import org.folio.am.integration.messaging.MessagingTestValues.Payload;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.MessageHandlingException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaHandlerTest {

  @Mock private KafkaTemplate<String, Payload> kafkaTemplate;
  @InjectMocks private KafkaHandler handler;

  @Test
  void handleMessage_positive() {
    var msg = genericMessage();

    when(kafkaTemplate.send(getEnvTopicName(DESTINATION), PAYLOAD)).thenReturn(null);

    handler.handleMessage(msg);
  }

  @Test
  void handleMessage_negative_emptyDestination() {
    var msg = genericMessageWithDestination("");

    assertThatThrownBy(() -> handler.handleMessage(msg))
      .isInstanceOf(MessageHandlingException.class)
      .hasMessageContaining("Destination is not present");
  }
}
