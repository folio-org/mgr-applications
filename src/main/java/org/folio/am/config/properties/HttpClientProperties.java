package org.folio.am.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.http-client")
public class HttpClientProperties {

  private long connectionTimeout;
  private long readTimeout;
}
