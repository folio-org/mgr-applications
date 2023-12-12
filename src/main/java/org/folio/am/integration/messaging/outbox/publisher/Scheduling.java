package org.folio.am.integration.messaging.outbox.publisher;

import lombok.Data;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
public class Scheduling {

  private static final boolean DEFAULT_ENABLED = false;
  private static final int DEFAULT_FIXED_DELAY = 60000;
  private static final boolean DEFAULT_QUICK_CHECK = true;
  private static final String DEFAULT_THREAD_NAME_PFX = "trx-outbox-scheduling-";

  private boolean enabled = DEFAULT_ENABLED;
  private int fixedDelay = DEFAULT_FIXED_DELAY;
  private boolean quickCheck = DEFAULT_QUICK_CHECK;

  @NestedConfigurationProperty
  private TaskSchedulingProperties taskScheduling = new TaskSchedulingProperties();

  public Scheduling() {
    taskScheduling.setThreadNamePrefix(DEFAULT_THREAD_NAME_PFX);
  }
}
