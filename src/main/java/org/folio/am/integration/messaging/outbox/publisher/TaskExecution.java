package org.folio.am.integration.messaging.outbox.publisher;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.Data;

@Data
public class TaskExecution {

  public static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER = new ThreadPoolExecutor.DiscardPolicy();

  private static final String DEFAULT_THREAD_NAME_PFX = "trx-outbox-task-";

  private String threadNamePrefix = DEFAULT_THREAD_NAME_PFX;

  private final Pool pool = new Pool();
  private final Shutdown shutdown = new Shutdown();

  public RejectedExecutionHandler getRejectedExecutionHandler() {
    return REJECTED_EXECUTION_HANDLER;
  }

  @Data
  public static class Pool {

    public static final int QUEUE_CAPACITY = 1;
    public static final int CORE_SIZE = 1;
    public static final int MAX_SIZE = 1;

    private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(60);
    private static final boolean DEFAULT_ALLOW_CORE_THREAD_TIMEOUT = true;

    /**
     * Whether core threads are allowed to time out. This enables dynamic growing and
     * shrinking of the pool.
     */
    private boolean allowCoreThreadTimeout = DEFAULT_ALLOW_CORE_THREAD_TIMEOUT;

    /**
     * Time limit for which threads may remain idle before being terminated.
     */
    private Duration keepAlive = DEFAULT_KEEP_ALIVE;

    public int getQueueCapacity() {
      return QUEUE_CAPACITY;
    }

    public int getCoreSize() {
      return CORE_SIZE;
    }

    public int getMaxSize() {
      return MAX_SIZE;
    }
  }

  @Data
  public static class Shutdown {

    /**
     * Whether the executor should wait for scheduled tasks to complete on shutdown.
     */
    private boolean awaitTermination;

    /**
     * Maximum time the executor should wait for remaining tasks to complete.
     */
    private Duration awaitTerminationPeriod;
  }
}
