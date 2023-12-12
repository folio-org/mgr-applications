package org.folio.am.integration.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.test.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessagingTestValues {

  public static final UUID PRIMARY_ID = UUID.fromString("88a7f7b7-321c-467e-8bd7-165923e9ffda");
  public static final Long CREATED = System.currentTimeMillis();
  public static final OffsetDateTime CREATED_AS_OFFSET = Instant.ofEpochMilli(CREATED).atOffset(ZoneOffset.UTC);
  public static final String HEADER = "header1";
  public static final String VALUE = "value1";
  public static final String DESTINATION = "destination";
  public static final Payload PAYLOAD =
    new Payload(UUID.fromString("e1a7509b-d2b2-4a07-b178-feb88a67876b"), "name1", "version1");
  public static final String PAYLOAD_SERIALIZED = TestUtils.asJsonString(PAYLOAD);

  public static Message<Payload> genericMessage() {
    return genericMessageWithPayload(PAYLOAD);
  }

  public static Message<Payload> genericMessageWithPrimaryId(UUID primaryId) {
    GenericMessageHeaderAccessor accessor = getGenericMessageHeaderAccessor();
    accessor.setPrimaryId(primaryId);

    return MessageBuilder.withPayload(PAYLOAD).setHeaders(accessor).build();
  }

  public static Message<Payload> genericMessageWithDestination(String destination) {
    GenericMessageHeaderAccessor accessor = getGenericMessageHeaderAccessor();
    accessor.setDestination(destination);

    return MessageBuilder.withPayload(PAYLOAD).setHeaders(accessor).build();
  }

  public static <T> Message<T> genericMessageWithPayload(T payload) {
    GenericMessageHeaderAccessor accessor = getGenericMessageHeaderAccessor();

    return MessageBuilder.withPayload(payload).setHeaders(accessor).build();
  }

  @NotNull
  private static GenericMessageHeaderAccessor getGenericMessageHeaderAccessor() {
    GenericMessageHeaderAccessor accessor = new GenericMessageHeaderAccessor();
    accessor.setPrimaryId(PRIMARY_ID);
    accessor.setCreated(CREATED);
    accessor.setDestination(DESTINATION);
    accessor.setHeader(HEADER, VALUE);
    return accessor;
  }

  public static Message<Payload> rawMessage() {
    return MessageBuilder.withPayload(PAYLOAD).setHeader(HEADER, VALUE).build();
  }

  public record Payload(UUID id, String name, String version) {}
}
