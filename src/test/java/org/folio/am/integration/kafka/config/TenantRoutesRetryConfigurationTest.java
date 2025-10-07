package org.folio.am.integration.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TenantRoutesRetryConfigurationTest {

  @Test
  void setRetryDelay_positive() {
    var config = new TenantRoutesRetryConfiguration();
    var customDelay = Duration.ofSeconds(5);

    config.setRetryDelay(customDelay);

    assertThat(config.getRetryDelay()).isEqualTo(customDelay);
  }

  @Test
  void setRetryAttempts_positive() {
    var config = new TenantRoutesRetryConfiguration();
    var customAttempts = 10L;

    config.setRetryAttempts(customAttempts);

    assertThat(config.getRetryAttempts()).isEqualTo(customAttempts);
  }
}
