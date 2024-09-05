package org.folio.am.it;

import static org.apache.commons.lang3.SystemProperties.JDK_INTERNAL_HTTP_CLIENT_DISABLE_HOST_NAME_VERIFICATION;

import org.springframework.context.annotation.Configuration;

@Configuration
public class DisableHostnameVerificationConfig {

  static {
    System.setProperty(JDK_INTERNAL_HTTP_CLIENT_DISABLE_HOST_NAME_VERIFICATION, "true");
  }
}
