package org.folio.am.integration.kong;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConditionalOnProperty("application.kong.enabled")
@ConfigurationProperties(prefix = "application.kong")
public class KongConfigurationProperties {

  /**
   * Defines if application manager is integrated with Kong API Gateway.
   */
  private boolean enabled;

  /**
   * Provides Kong API Gateway admin URL.
   */
  @NotBlank
  private String url;
}
