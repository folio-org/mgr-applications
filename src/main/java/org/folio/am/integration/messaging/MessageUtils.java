package org.folio.am.integration.messaging;

import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.messaging.Message;

@UtilityClass
public class MessageUtils {

  public static UUID messageId(Message<?> message) {
    UUID id = null;

    if (message != null) {
      var accessor = GenericMessageHeaderAccessor.getOrNewAccessor(message);
      id = accessor.getPrimaryId();
    }

    return id;
  }
}
