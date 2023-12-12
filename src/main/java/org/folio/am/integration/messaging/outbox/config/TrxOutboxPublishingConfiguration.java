package org.folio.am.integration.messaging.outbox.config;

import static org.folio.am.integration.messaging.outbox.publisher.TrxOutboxPollingPublisher.PUBLISHING_KAFKA_TEMPLATE_BEAN_NAME;
import static org.folio.am.integration.messaging.outbox.publisher.TrxOutboxPollingPublisher.PUBLISHING_TASK_EXECUTOR_BEAN_NAME;
import static org.folio.am.integration.messaging.outbox.publisher.TrxOutboxPollingPublisher.PUBLISHING_TRX_TEMPLATE_BEAN_NAME;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.am.integration.messaging.outbox.data.TrxOutboxRepository;
import org.folio.am.integration.messaging.outbox.publisher.Publishing;
import org.folio.am.integration.messaging.outbox.publisher.Scheduling;
import org.folio.am.integration.messaging.outbox.publisher.TrxOutboxPollingPublisher;
import org.folio.am.integration.messaging.outbox.publisher.lock.TrxOutboxLockManager;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@Import(TrxOutboxPublishingConfiguration.SchedulingConfiguration.class)
@EnableAsync
@RequiredArgsConstructor
public class TrxOutboxPublishingConfiguration {

  private final PlatformTransactionManager transactionManager;
  private final TrxOutboxRepository outboxRepository;

  @Bean
  @ConditionalOnMissingBean
  public AsyncConfigurer asyncConfigurer() {
    return new AsyncExceptionHandlerConfigurer();
  }

  @Bean(PUBLISHING_TASK_EXECUTOR_BEAN_NAME)
  public Executor trxOutboxExecutor(Publishing publishing) {
    var properties = publishing.getTaskExecution();

    var map = PropertyMapper.get();

    var executor = new ThreadPoolTaskExecutor();
    map.from(properties.getThreadNamePrefix()).to(executor::setThreadNamePrefix);
    map.from(properties.getRejectedExecutionHandler()).to(executor::setRejectedExecutionHandler);

    var pool = properties.getPool();
    map.from(pool.getCoreSize()).to(executor::setCorePoolSize);
    map.from(pool.getMaxSize()).to(executor::setMaxPoolSize);
    map.from(pool.getQueueCapacity()).to(executor::setQueueCapacity);
    map.from(pool.getKeepAlive().getSeconds()).as(Long::intValue).to(executor::setKeepAliveSeconds);
    map.from(pool.isAllowCoreThreadTimeout()).to(executor::setAllowCoreThreadTimeOut);

    map.from(properties.getShutdown().getAwaitTerminationPeriod()).whenNonNull().as(Duration::toMillis)
      .to(executor::setAwaitTerminationMillis);

    return executor;
  }

  @Bean
  public Publishing publishing(TrxOutboxProperties outboxProperties) {
    return outboxProperties.getPublishing();
  }

  @Bean(PUBLISHING_TRX_TEMPLATE_BEAN_NAME)
  public TransactionTemplate transactionTemplate() {
    var transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return transactionTemplate;
  }

  @Bean(PUBLISHING_KAFKA_TEMPLATE_BEAN_NAME)
  public KafkaTemplate<String, String> publishingKafkaTemplate(ProducerFactory<String, String> producerFactory) {
    return new KafkaTemplate<>(producerFactory,
      Collections.singletonMap(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
  }

  @Bean
  public TrxOutboxLockManager lockManager(Publishing publishing,
    @Qualifier(PUBLISHING_TRX_TEMPLATE_BEAN_NAME) TransactionTemplate transactionTemplate,
    JdbcTemplate jdbcTemplate) {
    return new TrxOutboxLockManager(publishing.getLocking(), transactionTemplate, jdbcTemplate);
  }

  @Bean
  public TrxOutboxPollingPublisher trxOutboxPollingPublisher(Publishing publishing, TrxOutboxLockManager lockManager,
    @Qualifier(PUBLISHING_TRX_TEMPLATE_BEAN_NAME) TransactionTemplate transactionTemplate,
    @Qualifier(PUBLISHING_KAFKA_TEMPLATE_BEAN_NAME) KafkaTemplate<String, String> kafkaTemplate) {
    return new TrxOutboxPollingPublisher(publishing, lockManager, transactionTemplate, outboxRepository, kafkaTemplate);
  }

  @ConditionalOnProperty("messaging.trx-outbox.publishing.scheduling.enabled")
  @EnableScheduling
  @RequiredArgsConstructor
  public static class SchedulingConfiguration {

    private final TrxOutboxPollingPublisher pollingPublisher;

    @Bean
    public Scheduling scheduling(Publishing publishing) {
      return publishing.getScheduling();
    }

    @Bean
    public ThreadPoolTaskScheduler publishingScheduledTaskExecutor(Scheduling scheduling) {
      var taskScheduling = scheduling.getTaskScheduling();

      var map = PropertyMapper.get();

      var executor = new ThreadPoolTaskScheduler();

      executor.setPoolSize(1); // always 1, property's value ignored
      map.from(taskScheduling.getThreadNamePrefix()).to(executor::setThreadNamePrefix);

      var shutdown = taskScheduling.getShutdown();
      map.from(shutdown.isAwaitTermination()).to(executor::setWaitForTasksToCompleteOnShutdown);
      map.from(shutdown.getAwaitTerminationPeriod()).whenNonNull().as(Duration::toMillis)
        .to(executor::setAwaitTerminationMillis);

      return executor;
    }

    @Bean
    public SchedulingConfigurer schedulingConfigurer(Scheduling scheduling,
      @Qualifier("publishingScheduledTaskExecutor") ThreadPoolTaskScheduler taskScheduler) {
      return new TrxOutboxSchedulingConfigurer(scheduling, taskScheduler, pollingPublisher);
    }

    @RequiredArgsConstructor
    private static final class TrxOutboxSchedulingConfigurer implements SchedulingConfigurer {

      private final Scheduling scheduling;
      private final ThreadPoolTaskScheduler taskScheduler;
      private final TrxOutboxPollingPublisher pollingPublisher;

      @Override
      public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler);

        taskRegistrar.addFixedDelayTask(new IntervalTask(
          pollingPublisher::scheduledPublish,
          Duration.ofMillis(scheduling.getFixedDelay()))
        );
      }
    }
  }

  private static final class AsyncExceptionHandlerConfigurer implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
      return (ex, method, params) -> log.error("Async method [{}] failed with exception: "
        + ex.getMessage(), method, ex);
    }
  }
}
