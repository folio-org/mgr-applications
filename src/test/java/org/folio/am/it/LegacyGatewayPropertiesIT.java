package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
class LegacyGatewayPropertiesIT {

  private static final String ADMIN_CLIENT_BEAN = "folioKongAdminClient";
  private static final String DISCOVERY_LISTENER_BEAN = "apiGatewayDiscoveryListener";
  private static final String DEPRECATION_WARNING =
    "Configuration property 'application.kong.enabled' is deprecated and will be removed in the Vetch release. "
      + "Use 'application.apigw.enabled' instead.";

  @Nested
  @ExtendWith(OutputCaptureExtension.class)
  @TestPropertySource(properties = "application.kong.enabled=true")
  class LegacyPropertyEnabled extends BaseIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void gatewayIntegration_positive_legacyPropertyEnablesBeans() {
      assertThat(applicationContext.containsBean(ADMIN_CLIENT_BEAN)).isTrue();
      assertThat(applicationContext.containsBean(DISCOVERY_LISTENER_BEAN)).isTrue();
    }

    @Test
    void gatewayIntegration_positive_legacyPropertyIsReportedAsDeprecated(CapturedOutput output) {
      assertThat(output.getAll()).contains(DEPRECATION_WARNING);
    }
  }

  @Nested
  @TestPropertySource(properties = "KONG_INTEGRATION_ENABLED=false")
  class LegacyVariableDisabled extends BaseIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void gatewayIntegration_positive_legacyVariableDisablesBeans() {
      assertThat(applicationContext.containsBean(ADMIN_CLIENT_BEAN)).isFalse();
      assertThat(applicationContext.containsBean(DISCOVERY_LISTENER_BEAN)).isFalse();
    }
  }

  @Nested
  @TestPropertySource(properties = {"KONG_INTEGRATION_ENABLED=false", "APIGW_ENABLED=true"})
  class NewVariableOverridesLegacyVariable extends BaseIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void gatewayIntegration_positive_newVariableTakesPrecedence() {
      assertThat(applicationContext.containsBean(ADMIN_CLIENT_BEAN)).isTrue();
      assertThat(applicationContext.containsBean(DISCOVERY_LISTENER_BEAN)).isTrue();
    }
  }
}
