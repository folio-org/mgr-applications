package org.folio.am.integration.messaging;

import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

public class GenericMessageHeaderAccessor extends MessageHeaderAccessor {

  public static final String PRIMARY_ID_HEADER = "primaryId";
  public static final String DESTINATION_HEADER = "destination";
  public static final String CREATED_HEADER = "created";

  private static final IdGenerator ID_GENERATOR = new AlternativeJdkIdGenerator();

  public GenericMessageHeaderAccessor() {
    initHeaders(null);
  }

  protected GenericMessageHeaderAccessor(Message<?> message) {
    super(message);
    initHeaders(message);
  }

  public static GenericMessageHeaderAccessor wrap(Message<?> message) {
    return new GenericMessageHeaderAccessor(message);
  }

  public static GenericMessageHeaderAccessor getOrNewAccessor(Message<?> message) {
    var accessor = MessageHeaderAccessor.getAccessor(message, GenericMessageHeaderAccessor.class);

    if (accessor == null) {
      accessor = wrap(message);
    }

    return accessor;
  }

  @Nullable
  public UUID getPrimaryId() {
    return getHeader(PRIMARY_ID_HEADER, UUID.class);
  }

  public void setPrimaryId(@Nullable UUID primaryId) {
    setHeader(PRIMARY_ID_HEADER, primaryId);
  }

  @Nullable
  public String getDestination() {
    return getHeader(DESTINATION_HEADER, String.class);
  }

  public void setDestination(@Nullable String destination) {
    setHeader(DESTINATION_HEADER, destination);
  }

  @Nullable
  public Long getCreated() {
    return getHeader(CREATED_HEADER, Long.class);
  }

  public void setCreated(@Nullable Long createdMillis) {
    setHeader(CREATED_HEADER, createdMillis);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T getHeader(String key, Class<T> type) {
    Object value = getHeader(key);
    if (value == null) {
      return null;
    }
    if (!type.isAssignableFrom(value.getClass())) {
      throw new IllegalArgumentException("Incorrect type specified for header '" + key + "'. Expected [" + type
        + "] but actual type is [" + value.getClass() + "]");
    }
    return (T) value;
  }

  @Override
  protected MessageHeaderAccessor createAccessor(Message<?> message) {
    return wrap(message);
  }

  private void initHeaders(Message<?> message) {
    UUID primaryId = null;
    Long created = null;

    // test if incoming message contains the headers already
    if (message != null) {
      primaryId = message.getHeaders().get(PRIMARY_ID_HEADER, UUID.class);
      created = message.getHeaders().get(CREATED_HEADER, Long.class);
    }

    if (primaryId == null) {
      primaryId = ID_GENERATOR.generateId();
      setPrimaryId(primaryId);
    }

    if (created == null) {
      created = System.currentTimeMillis();
      setCreated(created);
    }
  }
}
