package org.folio.am.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.CREATED_HEADER;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.DESTINATION_HEADER;
import static org.folio.am.integration.messaging.GenericMessageHeaderAccessor.PRIMARY_ID_HEADER;

import java.util.UUID;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;

@UnitTest
class GenericMessageHeaderAccessorTest {

  @Test
  void created_with_headersPreset() {
    var now = System.currentTimeMillis();
    var accessor = new GenericMessageHeaderAccessor();

    var primaryId = accessor.getMessageHeaders().get(PRIMARY_ID_HEADER, UUID.class);
    assertThat(primaryId).isNotNull();

    var created = accessor.getMessageHeaders().get(CREATED_HEADER, Long.class);
    assertThat(created)
      .isNotNull()
      .isGreaterThanOrEqualTo(now);
  }

  @Test
  void created_with_headersCopied_from_Message() {
    var expectedPrimaryId = UUID.randomUUID();
    var expectedCreated = System.currentTimeMillis();

    var accessor = new GenericMessageHeaderAccessor(message(expectedPrimaryId, expectedCreated));

    var primaryId = accessor.getMessageHeaders().get(PRIMARY_ID_HEADER, UUID.class);
    assertThat(primaryId).isEqualTo(expectedPrimaryId);

    var created = accessor.getMessageHeaders().get(CREATED_HEADER, Long.class);
    assertThat(created).isEqualTo(expectedCreated);
  }

  @Test
  void getOrNewAccessor_returns_equalAccessor() {
    var expected = new GenericMessageHeaderAccessor();

    Message<?> msg = message(expected);

    var actual = GenericMessageHeaderAccessor.getOrNewAccessor(msg);

    assertThat(actual).isNotNull();
    assertThat(actual.getMessageHeaders())
      .contains(
        entry(PRIMARY_ID_HEADER, expected.getMessageHeaders().get(PRIMARY_ID_HEADER)),
        entry(CREATED_HEADER, expected.getMessageHeaders().get(CREATED_HEADER))
      );
  }

  @Test
  void primaryId_setter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = UUID.randomUUID();
    accessor.setPrimaryId(expected);
    var actual = accessor.getMessageHeaders().get(PRIMARY_ID_HEADER, UUID.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void primaryId_getter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = accessor.getMessageHeaders().get(PRIMARY_ID_HEADER, UUID.class);
    var actual = accessor.getPrimaryId();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void destination_setter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = RandomStringUtils.random(10);
    accessor.setDestination(expected);
    var actual = accessor.getMessageHeaders().get(DESTINATION_HEADER, String.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void destination_getter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = RandomStringUtils.random(10);
    accessor.setDestination(expected);
    var actual = accessor.getDestination();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void created_setter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = System.currentTimeMillis();
    accessor.setCreated(expected);
    var actual = accessor.getMessageHeaders().get(CREATED_HEADER, Long.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void created_getter() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = accessor.getMessageHeaders().get(CREATED_HEADER, Long.class);
    var actual = accessor.getCreated();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getHeader_positive() {
    var accessor = new GenericMessageHeaderAccessor();

    var expected = new Value();
    accessor.setHeader("header1", expected);

    var actual = accessor.getHeader("header1", Value.class);

    assertThat(actual).isSameAs(expected);
  }

  @Test
  void getHeader_positive_nullValue() {
    var accessor = new GenericMessageHeaderAccessor();

    var actual = accessor.getHeader("header1", Value.class);

    assertThat(actual).isNull();
  }

  @Test
  void getHeader_negative_incorrectClass() {
    var accessor = new GenericMessageHeaderAccessor();

    accessor.setHeader("header1", "value1");

    assertThatThrownBy(() -> accessor.getHeader("header1", Value.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Incorrect type specified for header");
  }

  private static Message<?> message(GenericMessageHeaderAccessor expected) {
    return MessageBuilder.withPayload(new Object())
      .setHeaders(expected)
      .build();
  }

  private static Message<?> message(UUID primaryId, Long created) {
    return MessageBuilder.withPayload(new Object())
      .setHeader(PRIMARY_ID_HEADER, primaryId)
      .setHeader(CREATED_HEADER, created)
      .build();
  }

  private record Value() {
  }
}
