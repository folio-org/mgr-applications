package org.folio.am.integration.messaging.channel.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.integration.messaging.MessagingTestValues.DESTINATION;
import static org.mockito.Mockito.when;

import org.folio.am.integration.messaging.GenericMessageHeaderAccessor;
import org.folio.am.integration.messaging.MessagingTestValues;
import org.folio.test.types.UnitTest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.AbstractMessageChannel;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DestinationSetterInterceptorTest {

  @Mock
  private AbstractMessageChannel channel;
  private final DestinationSetterInterceptor interceptor = new DestinationSetterInterceptor();

  @Test
  void doNothing_if_destinationPresent() {
    var msg = MessagingTestValues.genericMessage();
    var expected = getDestination(msg);

    var actual = interceptor.preSend(msg, channel);

    assertThat(actual).isNotNull();
    assertThat(getDestination(actual)).isEqualTo(expected);
  }

  @Test
  void populateFromBeanName_if_destinationPresent() {
    var msg = MessagingTestValues.genericMessageWithDestination("");

    when(channel.getBeanName()).thenReturn(DESTINATION);

    var actual = interceptor.preSend(msg, channel);

    assertThat(actual).isNotNull();
    assertThat(getDestination(actual)).isEqualTo(DESTINATION);
  }

  @Nullable
  private static String getDestination(Message<?> msg) {
    return msg.getHeaders().get(GenericMessageHeaderAccessor.DESTINATION_HEADER, String.class);
  }
}
