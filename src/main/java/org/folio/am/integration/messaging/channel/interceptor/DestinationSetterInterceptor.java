package org.folio.am.integration.messaging.channel.interceptor;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.GenericMessageHeaderAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

@Log4j2
public class DestinationSetterInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    var msg = message;

    if (!containsDestination(msg.getHeaders())) {
      log.debug("Message headers don't contain '{}' header. Populating from channel's name ...",
        GenericMessageHeaderAccessor.DESTINATION_HEADER);

      var accessor = GenericMessageHeaderAccessor.getOrNewAccessor(msg);

      var destination = getDestination(channel);
      accessor.setDestination(destination);

      msg = MessageBuilder.createMessage(msg.getPayload(), accessor.getMessageHeaders());

      log.debug("'{}' header set to: {}", GenericMessageHeaderAccessor.DESTINATION_HEADER, destination);
    }

    return msg;
  }

  private boolean containsDestination(MessageHeaders headers) {
    return isNotBlank(headers.get(GenericMessageHeaderAccessor.DESTINATION_HEADER, String.class));
  }

  private static String getDestination(MessageChannel channel) {
    return ((AbstractMessageChannel) channel).getBeanName();
  }
}
