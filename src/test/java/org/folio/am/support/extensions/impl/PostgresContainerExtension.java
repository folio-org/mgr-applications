package org.folio.am.support.extensions.impl;

import static java.lang.String.valueOf;

import java.util.UUID;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresContainerExtension implements BeforeAllCallback, AfterAllCallback {

  public static final String POSTGRES_NETWORK_ALIAS = UUID.randomUUID().toString();
  private static final String DB_HOST_PROPERTY = "DB_HOST";
  private static final String DB_PORT_PROPERTY = "DB_PORT";

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:12-alpine")
    .withDatabaseName("postgres")
    .withEnv("PG_USER", "postgres")
    .withUsername("postgres")
    .withPassword("postgres_admin")
    .withNetwork(Network.SHARED)
    .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
    .withInitScript("sql/init-database.sql");

  @Override
  public void beforeAll(ExtensionContext context) {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }

    System.setProperty(DB_HOST_PROPERTY, CONTAINER.getHost());
    System.setProperty(DB_PORT_PROPERTY, valueOf(CONTAINER.getMappedPort(5432)));
  }

  @Override
  public void afterAll(ExtensionContext context) {
    System.clearProperty(DB_HOST_PROPERTY);
    System.clearProperty(DB_PORT_PROPERTY);
  }
}
