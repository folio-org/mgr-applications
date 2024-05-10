package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@EnableOkapiSecurity
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.okapi.enabled=false",
  "application.kong.module-self-url=https://test-mgr-applications:443",
  "application.kong.register-module=true",
  "application.kong.tls.enabled=true"
})
class KongRegistrationIT extends BaseIntegrationTest {

  @Autowired private KongAdminClient kongAdminClient;

  @Test
  void verifyModuleRegistration() {
    var moduleName = "mgr-applications-1.0.0";
    var service = kongAdminClient.getService(moduleName);
    assertThat(service).satisfies(s -> {
      assertThat(s.getProtocol()).isEqualTo("https");
      assertThat(s.getPort()).isEqualTo(443);
      assertThat(s.getHost()).isEqualTo("test-mgr-applications");
    });

    var routes = kongAdminClient.getRoutesByTag(moduleName, null);
    assertThat(routes.getData()).hasSize(14);
  }
}
