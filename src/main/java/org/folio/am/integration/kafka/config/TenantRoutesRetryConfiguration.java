package org.folio.am.integration.kafka.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("application.retry.tenant-routes")
public class TenantRoutesRetryConfiguration {

  @NotNull
  private Duration retryDelay;

  @NotNull
  @Positive
  private long retryAttempts;
}
