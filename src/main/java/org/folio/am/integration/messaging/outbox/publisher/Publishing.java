package org.folio.am.integration.messaging.outbox.publisher;

import lombok.Data;
import org.folio.am.integration.messaging.outbox.publisher.lock.Locking;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
public class Publishing {

  private static final int DEFAULT_FETCH_SIZE = 100;

  private int fetchSize = DEFAULT_FETCH_SIZE;

  @NestedConfigurationProperty
  private TaskExecution taskExecution = new TaskExecution();
  @NestedConfigurationProperty
  private Scheduling scheduling = new Scheduling();
  @NestedConfigurationProperty
  private Locking locking = new Locking();
}
