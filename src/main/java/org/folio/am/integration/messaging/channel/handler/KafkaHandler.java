package org.folio.am.integration.messaging.channel.handler;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.GenericMessageHeaderAccessor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;

@Log4j2
@RequiredArgsConstructor
public class KafkaHandler implements MessageHandler {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public void handleMessage(Message<?> message) throws MessagingException {
    log.debug("Sending message to Kafka: {}", message);

    var accessor = GenericMessageHeaderAccessor.wrap(message);

    var destination = accessor.getDestination();
    if (isBlank(destination)) {
      throw new MessageHandlingException(message,
        "Destination is not present in the message header: '" + GenericMessageHeaderAccessor.DESTINATION_HEADER + "'");
    }

    var topic = getEnvTopicName(destination);
    kafkaTemplate.send(topic, message.getPayload());

    log.debug("Message sent to the topic: {}", topic);
  }
}
