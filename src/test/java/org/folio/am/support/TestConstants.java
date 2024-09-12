package org.folio.am.support;

import static org.folio.am.support.TestUtils.httpClientWithDummySslContext;

import java.net.http.HttpClient;
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

  /**
   * Sample JWT that will expire in 2030 year for test_tenant with randomly generated user id.
   */
  public static final String OKAPI_AUTH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmb2xpbyIsInVzZXJfaWQiOiJlNmQyODVlOS03M"
    + "mVkLTQxYTQtOGIzYi01Y2VlNGNiYzg0MjUiLCJ0eXBlIjoiYWNjZXNzIiwiZXhwIjoxODkzNTAyODAwLCJpYXQiOjE3MjUzMDM2ODgsInRlbmFud"
    + "CI6InRlc3RfdGVuYW50In0.SdtIQTrn7_XPnyi75Ai9bBkCWa8eQ69U6VAidCCRFFQ";

  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_USER = "USER";

  public static final HttpClient HTTP_CLIENT_DUMMY_SSL = httpClientWithDummySslContext();

  public static String id(String name, String version) {
    return name + "-" + version;
  }
}
