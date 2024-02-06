package org.folio.am.support.extensions.impl;

import static java.time.Duration.ofSeconds;
import static org.folio.am.support.extensions.impl.PostgresContainerExtension.POSTGRES_NETWORK_ALIAS;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

@Slf4j
public class KongGatewayExtension implements BeforeAllCallback, AfterAllCallback {

  public static final String KONG_GATEWAY_URL_PROPERTY = "kong.gateway.url";
  public static final String KONG_DOCKER_IMAGE = "kong:3.4.1-ubuntu";
  public static final String KONG_URL_PROPERTY = "kong.url";

  @SuppressWarnings("resource")
  private static final GenericContainer<?> CONTAINER = new GenericContainer<>(KONG_DOCKER_IMAGE)
    .withEnv(kongEnvironment())
    .withNetwork(Network.SHARED)
    .withExposedPorts(8000, 8001)
    .withLogConsumer(new Slf4jLogConsumer(log))
    .withAccessToHost(true);

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (!CONTAINER.isRunning()) {
      runMigrationWithContainer("kong migrations bootstrap");
      runMigrationWithContainer("kong migrations up && kong migrations finish");
      CONTAINER.start();
    }

    System.setProperty(KONG_URL_PROPERTY, getUrlForExposedPort(8001));
    System.setProperty(KONG_GATEWAY_URL_PROPERTY, getUrlForExposedPort(8000));
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    System.clearProperty(KONG_URL_PROPERTY);
    System.clearProperty(KONG_GATEWAY_URL_PROPERTY);
  }

  private static String getUrlForExposedPort(int port) {
    return String.format("http://%s:%s", CONTAINER.getHost(), CONTAINER.getMappedPort(port));
  }

  private static void runMigrationWithContainer(String command) {
    try (var bootstrapMigrations = migrationContainer(command)) {
      bootstrapMigrations.start();
    } catch (Exception e) {
      throw new RuntimeException("Failed to run kong migrations", e);
    }
  }

  private static GenericContainer<?> migrationContainer(String command) {
    return new GenericContainer<>(KONG_DOCKER_IMAGE)
      .withEnv(kongMigrationEnvironment())
      .withCommand(command)
      .withNetwork(Network.SHARED)
      .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(ofSeconds(5)));
  }

  private static Map<String, String> kongMigrationEnvironment() {
    var environment = new LinkedHashMap<String, String>();

    environment.put("KONG_DATABASE", "postgres");
    environment.put("KONG_PG_DATABASE", "kong_it");
    environment.put("KONG_PG_USER", "kong_admin");
    environment.put("KONG_PG_PASSWORD", "kong123");
    environment.put("KONG_PG_PORT", "5432");
    environment.put("KONG_PG_HOST", POSTGRES_NETWORK_ALIAS);
    environment.put("KONG_ROUTER_FLAVOR", "expressions");

    return environment;
  }

  private static Map<String, String> kongEnvironment() {
    var environment = new LinkedHashMap<>(kongMigrationEnvironment());

    environment.put("KONG_PROXY_ACCESS_LOG", "/dev/stdout");
    environment.put("KONG_ADMIN_ACCESS_LOG", "/dev/stdout");
    environment.put("KONG_PROXY_ERROR_LOG", "/dev/stderr");
    environment.put("KONG_ADMIN_ERROR_LOG", "/dev/stderr");
    environment.put("KONG_PROXY_LISTEN", "0.0.0.0:8000");
    environment.put("KONG_ADMIN_LISTEN", "0.0.0.0:8001");
    environment.put("KONG_PLUGINS", "bundled");
    environment.put("KONG_LOG_LEVEL", "info");
    environment.put("KONG_WORKER_STATE_UPDATE_FREQUENCY", "2");

    return environment;
  }
}
