package org.folio.am.integration.mte;

import lombok.Data;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.common.utils.tls.HttpClientTlsUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Data
@Configuration
@ConfigurationProperties(prefix = "tenant.entitlement")
@ConditionalOnFarModeDisabled
public class TenantEntitlementConfiguration {

  private String url;

  private TlsProperties tls;

  @Bean
  public TenantEntitlementClient tenantEntitlementClient() {
    return HttpClientTlsUtils.buildHttpServiceClient(RestClient.builder(), tls, url, TenantEntitlementClient.class);
  }

  @Bean
  public EntitlementService entitlementService(TenantEntitlementClient client) {
    return new EntitlementService(client);
  }
}
