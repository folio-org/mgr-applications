package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_NAME;
import static org.folio.am.support.TestConstants.MODULE_BAR_URL;
import static org.folio.am.support.TestConstants.MODULE_BAR_VERSION;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_NAME;
import static org.folio.am.support.TestConstants.MODULE_FOO_URL;
import static org.folio.am.support.TestConstants.MODULE_FOO_VERSION;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.folio.am.integration.kafka.model.TenantEntitlementEvent;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.am.support.config.KafkaTestConfiguration;
import org.folio.common.utils.OkapiHeaders;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@Import(KafkaTestConfiguration.class)
@TestPropertySource(properties = {
  "application.okapi.enabled=false",
  "application.kong.enabled=true",
  "application.kong.tenant-checks.enabled=true"
})
class EntitlementEventListenerIT extends BaseIntegrationTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  private static final String TENANT_3 = "tenant3";

  @Value("${spring.kafka.topics.entitlement}")
  private String entitlementTopic;

  @Autowired private KongAdminClient kongAdminClient;
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @BeforeAll
  static void setUp(@Autowired ApplicationContext applicationContext) {
    assertThat(applicationContext.containsBean("folioKongAdminClient")).isTrue();
    assertThat(applicationContext.containsBean("folioKongGatewayService")).isTrue();
    assertThat(applicationContext.containsBean("kongDiscoveryListener")).isTrue();
    assertThat(applicationContext.containsBean("entitlementEventListener")).isTrue();
    assertThat(applicationContext.containsBean("entitlementKafkaListenerContainerFactory")).isTrue();
    assertThat(applicationContext.containsBean("okapiClient")).isFalse();
  }

  @AfterEach
  void tearDown() {
    deleteServiceSafely(MODULE_FOO_ID);
    deleteServiceSafely(MODULE_BAR_ID);
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_addFirstTenant() throws Exception {
    // Create module discovery to register Kong service and routes
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Verify initial route has wildcard expression
    var routesBefore = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
    assertThat(routesBefore.getData()).hasSize(1);
    var initialExpression = routesBefore.getData().getFirst().getExpression();
    assertThat(initialExpression).contains("x_okapi_tenant ~ r#\".*\"#");

    // Send ENTITLE event for first tenant
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);

    // Verify route expression updated to include specific tenant
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains("x_okapi_tenant");
      assertThat(expression).contains(TENANT_1);
      assertThat(expression).doesNotContain("~ r#\".*\"#"); // Wildcard removed
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_addMultipleTenants() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Send ENTITLE events for multiple tenants
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_2, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_3, TenantEntitlementEvent.Type.ENTITLE);

    // Verify all tenants are in route expression
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
      assertThat(expression).contains(TENANT_2);
      assertThat(expression).contains(TENANT_3);
      assertThat(expression).contains("||"); // OR operator between tenants
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_upgradeEvent() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Send UPGRADE event (should behave same as ENTITLE)
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.UPGRADE);

    // Verify tenant added
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_revokeTenant() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Add multiple tenants
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_2, TenantEntitlementEvent.Type.ENTITLE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
      assertThat(expression).contains(TENANT_2);
    });

    // Revoke one tenant
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.REVOKE);

    // Verify only TENANT_2 remains
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).doesNotContain(TENANT_1);
      assertThat(expression).contains(TENANT_2);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_revokeLastTenant() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Add one tenant
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
    });

    // Revoke last tenant - should restore wildcard
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.REVOKE);

    // Verify wildcard restored
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains("x_okapi_tenant ~ r#\".*\"#"); // Wildcard restored
      assertThat(expression).doesNotContain(TENANT_1);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_multipleModules() throws Exception {
    // Create discoveries for multiple modules
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);
    createModuleDiscovery(MODULE_BAR_ID, MODULE_BAR_NAME, MODULE_BAR_VERSION, MODULE_BAR_URL);

    // Add tenants to different modules
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_BAR_ID, TENANT_2, TenantEntitlementEvent.Type.ENTITLE);

    // Verify each module has its own tenant
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var fooRoutes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var fooExpression = fooRoutes.getData().getFirst().getExpression();
      assertThat(fooExpression).contains(TENANT_1);
      assertThat(fooExpression).doesNotContain(TENANT_2);

      var barRoutes = kongAdminClient.getServiceRoutes(MODULE_BAR_ID, null);
      var barExpression = barRoutes.getData().getFirst().getExpression();
      assertThat(barExpression).contains(TENANT_2);
      assertThat(barExpression).doesNotContain(TENANT_1);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_idempotentOperations() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Send same ENTITLE event twice
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);

    // Verify tenant appears only once
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(1);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
      // Count occurrences of tenant name - should appear only once
      var count = expression.split(TENANT_1, -1).length - 1;
      assertThat(count).isEqualTo(1);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_complexScenario() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Add multiple tenants
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_2, TenantEntitlementEvent.Type.ENTITLE);
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_3, TenantEntitlementEvent.Type.ENTITLE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1, TENANT_2, TENANT_3);
    });

    // Revoke middle tenant
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_2, TenantEntitlementEvent.Type.REVOKE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1, TENANT_3);
      assertThat(expression).doesNotContain(TENANT_2);
    });

    // Add tenant back
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_2, TenantEntitlementEvent.Type.ENTITLE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1, TENANT_2, TENANT_3);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_negative_revokeNonExistentTenant() throws Exception {
    // Create module discovery
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Add one tenant
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);

    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
    });

    // Try to revoke tenant that doesn't exist - should be idempotent
    sendEntitlementEvent(MODULE_FOO_ID, "non-existent-tenant", TenantEntitlementEvent.Type.REVOKE);

    // Verify existing tenant still present
    await().pollDelay(FIVE_SECONDS).atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      var expression = routes.getData().getFirst().getExpression();
      assertThat(expression).contains(TENANT_1);
    });
  }

  @Test
  @Sql(scripts = "classpath:/sql/module-discoveries-it.sql")
  void entitlementEvent_positive_multipleRoutesPerModule() throws Exception {
    // Create module discovery with multiple routes
    createModuleDiscovery(MODULE_FOO_ID, MODULE_FOO_NAME, MODULE_FOO_VERSION, MODULE_FOO_URL);

    // Verify initial routes - should have at least one route
    var routesBefore = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
    var routeCount = routesBefore.getData().size();
    assertThat(routeCount).isGreaterThan(0);

    // Send ENTITLE event
    sendEntitlementEvent(MODULE_FOO_ID, TENANT_1, TenantEntitlementEvent.Type.ENTITLE);

    // Verify all routes updated
    await().atMost(TEN_SECONDS).untilAsserted(() -> {
      var routes = kongAdminClient.getServiceRoutes(MODULE_FOO_ID, null);
      assertThat(routes.getData()).hasSize(routeCount);
      routes.getData().forEach(route -> {
        assertThat(route.getExpression()).contains(TENANT_1);
      });
    });
  }

  private void sendEntitlementEvent(String moduleId, String tenantName,
                                     TenantEntitlementEvent.Type type) {
    var event = TenantEntitlementEvent.of(moduleId, tenantName, UUID.randomUUID(), type);
    kafkaTemplate.send(entitlementTopic, event);
  }

  private void createModuleDiscovery(String moduleId, String moduleName,
                                      String moduleVersion, String url) throws Exception {
    var discovery = moduleDiscovery(moduleName, moduleVersion, url);
    mockMvc.perform(post("/modules/{id}/discovery", moduleId)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(discovery)))
      .andExpect(status().isCreated());
  }

  private void deleteServiceSafely(String serviceId) {
    try {
      kongAdminClient.getServiceRoutes(serviceId, null)
        .forEach(route -> kongAdminClient.deleteRoute(serviceId, route.getId()));
    } catch (Exception e) {
      // Ignore errors during cleanup
    }

    try {
      kongAdminClient.deleteService(serviceId);
    } catch (Exception e) {
      // Ignore errors during cleanup
    }
  }
}
