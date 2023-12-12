package org.folio.am.integration.messaging.outbox;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

public class TrxOutboxException extends MessagingException {

  public TrxOutboxException(Message<?> message) {
    super(message);
  }

  public TrxOutboxException(String description) {
    super(description);
  }

  public TrxOutboxException(String description, Throwable cause) {
    super(description, cause);
  }

  public TrxOutboxException(Message<?> message, String description) {
    super(message, description);
  }

  public TrxOutboxException(Message<?> message, Throwable cause) {
    super(message, cause);
  }

  public TrxOutboxException(Message<?> message, String description, Throwable cause) {
    super(message, description, cause);
  }
}
