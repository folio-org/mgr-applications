package org.folio.am.integration.okapi;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConditionalOnProperty("application.okapi.enabled")
@ConfigurationProperties(prefix = "application.okapi")
public class OkapiConfigurationProperties {

  /**
   * Defines if application manager is integrated with Okapi.
   */
  private boolean enabled;

  /**
   * Provides Okapi URL.
   */
  @NotBlank
  private String url;
}
