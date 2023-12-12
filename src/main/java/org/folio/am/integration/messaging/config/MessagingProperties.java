package org.folio.am.integration.messaging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("messaging")
public class MessagingProperties {

  // default send timeout in milliseconds, any negative value means indefinite timeout
  private long sendTimeout = -1;
}
