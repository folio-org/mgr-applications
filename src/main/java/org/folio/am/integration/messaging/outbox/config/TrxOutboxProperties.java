package org.folio.am.integration.messaging.outbox.config;

import lombok.Data;
import org.folio.am.integration.messaging.outbox.publisher.Publishing;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "messaging.trx-outbox")
public class TrxOutboxProperties {

  private boolean enabled;

  @NestedConfigurationProperty
  private Publishing publishing = new Publishing();
}
