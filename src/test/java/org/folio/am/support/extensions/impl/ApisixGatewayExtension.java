package org.folio.am.support.extensions.impl;

import static org.folio.test.extensions.impl.DockerImageRegistry.getApisixImageName;
import static org.folio.test.extensions.impl.DockerImageRegistry.getEtcdImageName;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class ApisixGatewayExtension implements BeforeAllCallback, AfterAllCallback {

  public static final String APISIX_URL_PROPERTY = "apisix.url";
  public static final String APISIX_PROXY_URL_PROPERTY = "apisix.gateway.url";
  public static final String APISIX_ADMIN_KEY = "edd1c9f034335f136f87ad84b625c8f1";

  private static final String ENV_APISIX_READINESS_TIMEOUT = "TESTCONTAINERS_APISIX_READINESS_TIMEOUT";
  private static final long DEFAULT_CONTAINER_READINESS_TIMEOUT = 120;
  // Logged by the folio-apisix entrypoint once the Admin API is up and the FOLIO startup
  // configuration (global rules + declarative resources) has been applied and verified.
  private static final String APISIX_INIT_DONE_LOG = ".*APISIX initialization finished successfully.*\\n";
  // folio-apisix config.yaml hardcodes the etcd endpoint to http://etcd:2379.
  private static final String ETCD_NETWORK_ALIAS = "etcd";

  private static final GenericContainer<?> ETCD_CONTAINER = etcdContainer(getEtcdImageName());
  private static final GenericContainer<?> APISIX_CONTAINER = apisixContainer(getApisixImageName());

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (!ETCD_CONTAINER.isRunning()) {
      ETCD_CONTAINER.start();
    }

    if (!APISIX_CONTAINER.isRunning()) {
      APISIX_CONTAINER.start();
    }

    System.setProperty(APISIX_URL_PROPERTY, getUrlForExposedPort(9180));
    System.setProperty(APISIX_PROXY_URL_PROPERTY, getUrlForExposedPort(9080));
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    System.clearProperty(APISIX_URL_PROPERTY);
    System.clearProperty(APISIX_PROXY_URL_PROPERTY);
  }

  private static String getUrlForExposedPort(int port) {
    return String.format("http://%s:%s", APISIX_CONTAINER.getHost(), APISIX_CONTAINER.getMappedPort(port));
  }

  @SuppressWarnings("resource")
  private static GenericContainer<?> etcdContainer(String imageName) {
    return new GenericContainer<>(imageName)
      .withEnv(Map.of(
        "ETCD_DATA_DIR", "/etcd-data",
        "ETCD_LISTEN_CLIENT_URLS", "http://0.0.0.0:2379",
        "ETCD_ADVERTISE_CLIENT_URLS", "http://" + ETCD_NETWORK_ALIAS + ":2379"))
      .withNetwork(Network.SHARED)
      .withNetworkAliases(ETCD_NETWORK_ALIAS)
      .withExposedPorts(2379)
      .waitingFor(Wait.forListeningPort());
  }

  @SuppressWarnings("resource")
  private static GenericContainer<?> apisixContainer(String imageName) {
    return new GenericContainer<>(imageName)
      .withEnv("APISIX_ADMIN_KEY", APISIX_ADMIN_KEY)
      .withNetwork(Network.SHARED)
      .withNetworkAliases("apisix")
      .withExposedPorts(9080, 9180)
      .withAccessToHost(true)
      .dependsOn(ETCD_CONTAINER)
      .waitingFor(Wait.forLogMessage(APISIX_INIT_DONE_LOG, 1)
        .withStartupTimeout(Duration.ofSeconds(containerReadinessTimeout())));
  }

  private static long containerReadinessTimeout() {
    var env = System.getenv();
    return Long.parseLong(
      env.getOrDefault(ENV_APISIX_READINESS_TIMEOUT, String.valueOf(DEFAULT_CONTAINER_READINESS_TIMEOUT)));
  }
}
