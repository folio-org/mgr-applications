package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.extensions.impl.ApisixGatewayExtension.APISIX_ADMIN_KEY;

import java.util.Map;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.am.support.extensions.EnableApisixGateway;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.apisix.client.ApisixAdminClient;
import org.folio.tools.apisix.client.ApisixAdminClient.ApisixEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@EnableApisixGateway
@TestPropertySource(properties = {
  "application.security.enabled=true",
  "application.keycloak.enabled=false",
  "application.apigw.type=apisix",
  "application.apigw.url=${apisix.url}",
  "application.apigw.api-key=" + APISIX_ADMIN_KEY,
  "application.apigw.module-self-url=http://test-mgr-applications:8081",
  "application.apigw.register-module=true",
})
class ApisixRegistrationIT extends BaseIntegrationTest {

  @Autowired private ApisixAdminClient apisixAdminClient;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void verifyModuleRegistration() {
    var moduleName = "mgr-applications-4.0.0";
    var service = apisixAdminClient.getService(moduleName).getValue();
    assertThat(service).isNotNull().satisfies(stored -> {
      assertThat(stored.getUpstream().getScheme()).isEqualTo("http");
      assertThat(stored.getUpstream().getNodes()).isEqualTo(Map.of("test-mgr-applications:8081", 1));
    });

    var moduleRoutes = apisixAdminClient.getRoutes(1, 100).getList().stream()
      .map(ApisixEntry::getValue)
      .filter(route -> route != null && route.getLabels() != null
        && moduleName.equals(route.getLabels().get("module")))
      .toList();
    assertThat(moduleRoutes).hasSize(20);
  }

  @Test
  void gatewayTypeSelection_positive_apisixBeansActive() {
    assertThat(applicationContext.containsBean("folioApisixGatewayService")).isTrue();
    assertThat(applicationContext.containsBean("folioKongGatewayService")).isFalse();
  }
}
