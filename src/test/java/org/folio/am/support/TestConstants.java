package org.folio.am.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String APPLICATION_NAME = "test-app";
  public static final String APPLICATION_VERSION = "1.0.0";
  public static final String APPLICATION_ID = id(APPLICATION_NAME, APPLICATION_VERSION);

  public static final String MODULE_FOO_NAME = "test-module-foo";
  public static final String MODULE_FOO_VERSION = "1.0.0";
  public static final String MODULE_FOO_ID = MODULE_FOO_NAME + "-" + MODULE_FOO_VERSION;
  public static final String MODULE_FOO_URL = "http://test-module-foo:8080";
  public static final String MODULE_FOO_INTERFACE_ID = "test-foo-interface";

  public static final String MODULE_BAR_NAME = "test-module-bar";
  public static final String MODULE_BAR_VERSION = "1.0.0";
  public static final String MODULE_BAR_ID = MODULE_BAR_NAME + "-" + MODULE_BAR_VERSION;
  public static final String MODULE_BAR_URL = "http://test-module-bar:8080";
  public static final String MODULE_BAR_INTERFACE_ID = "test-bar-interface";

  public static final String SERVICE_NAME = "test-module";
  public static final String SERVICE_VERSION = "1.0.0";
  public static final String SERVICE_ID = id(SERVICE_NAME, SERVICE_VERSION);
  public static final String MODULE_ID = SERVICE_ID;
  public static final String MODULE_URL = "http://test-module:8080";
  public static final String UPDATED_URL = "http://test-module-updated:8080";

  public static final String OKAPI_AUTH_TOKEN = "X-Okapi-Token test value";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_USER = "USER";

  public static String id(String name, String version) {
    return name + "-" + version;
  }
}
