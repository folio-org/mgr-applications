package org.folio.am.integration.mte;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.Data;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@Configuration
@Import(FeignClientsConfiguration.class)
@ConfigurationProperties(prefix = "tenant.entitlement")
@ConditionalOnFarModeDisabled
public class TenantEntitlementConfiguration {

  private String url;

  @Bean
  public TenantEntitlementClient tenantEntitlementClient(Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract).encoder(encoder).decoder(decoder)
      .target(TenantEntitlementClient.class, url);
  }

  @Bean
  public EntitlementService entitlementService(TenantEntitlementClient client) {
    return new EntitlementService(client);
  }
}
