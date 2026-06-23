package org.folio.am.support.extensions.impl;

import static org.folio.am.support.extensions.impl.PostgresContainerExtension.POSTGRES_NETWORK_ALIAS;
import static org.folio.test.extensions.impl.DockerImageRegistry.getKongImageName;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

@Slf4j
public class KongGatewayExtension implements BeforeAllCallback, AfterAllCallback {

  public static final String KONG_GATEWAY_URL_PROPERTY = "kong.gateway.url";
  public static final String KONG_URL_PROPERTY = "kong.url";

  private static final String ENV_KONG_READINESS_TIMEOUT = "TESTCONTAINERS_KONG_READINESS_TIMEOUT";
  private static final long DEFAULT_CONTAINER_READINESS_TIMEOUT = 120;

  private static final long CONTAINER_READINESS_TIMEOUT;
  private static final GenericContainer<?> CONTAINER;

  static {
    var env = System.getenv();
    CONTAINER_READINESS_TIMEOUT = Long.parseLong(
      env.getOrDefault(ENV_KONG_READINESS_TIMEOUT, String.valueOf(DEFAULT_CONTAINER_READINESS_TIMEOUT)));
    CONTAINER = kongContainer(getKongImageName());
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (!CONTAINER.isRunning()) {
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

  @SuppressWarnings("resource")
  private static GenericContainer<?> kongContainer(String imageName) {
    return new GenericContainer<>(imageName)
      .withEnv(kongEnvironment())
      .withNetwork(Network.SHARED)
      .withExposedPorts(8000, 8001)
      .withAccessToHost(true)
      .waitingFor(Wait.forHttp("/status")
        .forPort(8001)
        .forStatusCode(200)
        .withStartupTimeout(Duration.ofSeconds(CONTAINER_READINESS_TIMEOUT)));
  }

  private static Map<String, String> kongEnvironment() {
    var environment = new LinkedHashMap<String, String>();

    environment.put("KONG_DATABASE", "postgres");
    environment.put("KONG_PG_DATABASE", "kong_it");
    environment.put("KONG_PG_USER", "kong_admin");
    environment.put("KONG_PG_PASSWORD", "kong123");
    environment.put("KONG_PG_PORT", "5432");
    environment.put("KONG_PG_HOST", POSTGRES_NETWORK_ALIAS);
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
