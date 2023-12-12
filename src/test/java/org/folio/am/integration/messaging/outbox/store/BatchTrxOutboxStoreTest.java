package org.folio.am.integration.messaging.outbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.integration.messaging.MessagingTestValues.CREATED_AS_OFFSET;
import static org.folio.am.integration.messaging.MessagingTestValues.DESTINATION;
import static org.folio.am.integration.messaging.MessagingTestValues.PAYLOAD_SERIALIZED;
import static org.folio.am.integration.messaging.MessagingTestValues.PRIMARY_ID;
import static org.folio.am.integration.messaging.MessagingTestValues.genericMessage;
import static org.folio.am.integration.messaging.MessagingTestValues.genericMessageWithDestination;
import static org.folio.am.integration.messaging.MessagingTestValues.genericMessageWithPrimaryId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.folio.am.integration.messaging.outbox.TrxOutboxException;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxEntity;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.folio.am.integration.messaging.outbox.event.OutboxUpdatedEvent;
import org.folio.test.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BatchTrxOutboxStoreTest {

  @Mock private TrxOutboxRepository repository;
  @Spy private ObjectMapper mapper = TestUtils.OBJECT_MAPPER;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private BatchTrxOutboxStore store;

  @Captor private ArgumentCaptor<List<TrxOutboxEntity>> entityCaptor;
  @Captor private ArgumentCaptor<OutboxUpdatedEvent> eventCaptor;

  @Test
  void save_positive() {
    var msg = genericMessage();

    store.saveMessage(msg);

    verifyNoInteractions(repository);
    assertSaved(msg);
  }

  @Test
  void save_negative_messageNull() {
    assertThatThrownBy(() -> store.saveMessage(null))
      .isInstanceOf(TrxOutboxException.class)
      .hasMessage("Message is null");
  }

  @Test
  void save_negative_primaryIdNull() {
    var msg = genericMessageWithPrimaryId(null);

    assertThatThrownBy(() -> store.saveMessage(msg))
      .isInstanceOf(TrxOutboxException.class)
      .hasMessage("Message id cannot be null");
  }

  @Test
  void save_negative_destinationNull() {
    var msg = genericMessageWithDestination(null);

    assertThatThrownBy(() -> store.saveMessage(msg))
      .isInstanceOf(TrxOutboxException.class)
      .hasMessage("Message destination cannot be null");
  }

  @Test
  void beforeCommit_positive() {
    store.cachedMessages.add(genericMessage());
    when(repository.saveAll(entityCaptor.capture())).thenReturn(null);

    store.beforeCommit(false);

    var entity = entityCaptor.getValue().get(0);

    assertThat(entity.getPayload()).isEqualTo(PAYLOAD_SERIALIZED);
    assertThat(entity.getMessageId()).isEqualTo(PRIMARY_ID);
    assertThat(entity.getDestination()).isEqualTo(DESTINATION);
    assertThat(entity.getCreated()).isEqualTo(CREATED_AS_OFFSET);
  }

  @Test
  void beforeCommit_positive_readOnlyTransaction() {
    store.cachedMessages.add(genericMessage());

    store.beforeCommit(true);

    verifyNoInteractions(repository);
  }

  @Test
  void beforeCommit_negative_jsonSerializationFailed() throws JsonProcessingException {
    store.cachedMessages.add(genericMessage());
    doThrow(JsonProcessingException.class).when(mapper).writeValueAsString(any());

    assertThatThrownBy(() -> store.beforeCommit(false))
      .isInstanceOf(TrxOutboxException.class)
      .hasMessageContaining("Failed to serialize object to a json string");
  }

  @Test
  void afterCommit_positive() {
    doNothing().when(eventPublisher).publishEvent(eventCaptor.capture());
    store.savedMessagesCount = 1;

    store.afterCommit();

    var event = eventCaptor.getValue();
    assertThat(event).isEqualTo(new OutboxUpdatedEvent(store, 1));
  }

  @Test
  void afterCommit_positive_noEvent() {
    store.savedMessagesCount = 0;

    store.afterCommit();

    verifyNoInteractions(eventPublisher);
  }

  private void assertSaved(Message<?> msg) {
    assertThat(store.savedMessagesCount).isEqualTo(1);
    assertThat(store.cachedMessages).containsOnly(msg);
  }
}
