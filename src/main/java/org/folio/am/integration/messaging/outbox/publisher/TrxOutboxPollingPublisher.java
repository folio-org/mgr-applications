package org.folio.am.integration.messaging.outbox.publisher;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxEntity;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.folio.am.integration.messaging.outbox.event.OutboxUpdatedEvent;
import org.folio.am.integration.messaging.outbox.publisher.lock.TrxOutboxLockManager;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@RequiredArgsConstructor
public class TrxOutboxPollingPublisher {

  public static final String PUBLISHING_TRX_TEMPLATE_BEAN_NAME = "publishingTransactionTemplate";
  public static final String PUBLISHING_TASK_EXECUTOR_BEAN_NAME = "publishingTaskExecutor";
  public static final String PUBLISHING_KAFKA_TEMPLATE_BEAN_NAME = "publishingKafkaTemplate";

  private final Publishing publishing;
  private final TrxOutboxLockManager lockManager;

  private final TransactionTemplate transactionTemplate;
  private final TrxOutboxRepository repository;

  private final KafkaTemplate<String, String> kafkaTemplate;

  @Async(PUBLISHING_TASK_EXECUTOR_BEAN_NAME)
  @EventListener
  public void handleOutboxUpdateEvent(OutboxUpdatedEvent event) {
    log.info("Outbox updated: messageCount = {}", event.getMessageCount());
    publish();
  }

  public void publish() {
    log.debug("Acquiring READ lock on outbox table");
    var lockOpt = lockManager.acquire(this);

    if (lockOpt.isEmpty()) {
      log.info("Outbox table lock cannot be acquired within the given timeout. Publishing interrupted");
      return;
    }
    var lock = lockOpt.get();
    log.debug("Lock acquired: {}", lock);

    try {
      publishMessages();
    } finally {
      log.debug("Releasing READ lock on outbox table");
      lockManager.release(lock);
      log.debug("Lock released");
    }
  }

  public void scheduledPublish() {
    log.debug("Scheduled publishing is called...");
    var callPublishing = true;

    if (publishing.getScheduling().isQuickCheck()) {
      log.debug("Running quick check to verify if anything has to be published");

      callPublishing = repository.isAnyData();

      log.debug("Quick check result: {}", callPublishing ? "Publishing is needed" : "Nothing to publish");
    }

    if (callPublishing) {
      publish();
    }
  }

  private void publishMessages() {
    log.info("Publishing messages from outbox");
    var started = System.currentTimeMillis();

    var batch = loadBatch();
    var batchNumber = 1;

    while (isNotEmpty(batch)) {
      processBatch(batch, batchNumber);

      batch = loadBatch();
      batchNumber++;
    }

    log.info("All messages published in {}mills", System.currentTimeMillis() - started);
  }

  private void processBatch(List<TrxOutboxEntity> batch, int batchNumber) {
    log.info("Processing {}th batch of messages: messageCount = {}", batchNumber, batch.size());

    transactionTemplate.executeWithoutResult(status -> {
      batch.forEach(this::send);

      deleteBatch(batch);
    });
  }

  private void deleteBatch(List<TrxOutboxEntity> batch) {
    var first = batch.get(0);
    var last = batch.get(batch.size() - 1);

    log.debug("Removing messages from outbox table: startingFromId = {}, endingWithId = {}",
      first.getId(), last.getId());
    repository.deleteAllByIdBetween(first.getId(), last.getId());
  }

  private void send(TrxOutboxEntity entity) {
    var topic = getEnvTopicName(entity.getDestination());

    log.debug("Sending message to topic: messageId = {}, payload = {}, topic = {}", entity.getMessageId(),
      entity.getPayload(), topic);
    kafkaTemplate.send(topic, entity.getPayload()); // temporary ignore Future<> returned by send() method
  }

  private List<TrxOutboxEntity> loadBatch() {
    return repository.findAllOrderedByIdAndLimitedTo(publishing.getFetchSize());
  }
}
