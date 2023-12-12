package org.folio.am.integration.kong;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.extern.log4j.Log4j2;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Log4j2
@Configuration
@Import(FeignClientsConfiguration.class)
@ConditionalOnBean(KongConfigurationProperties.class)
@ConditionalOnFarModeDisabled
public class KongConfiguration {

  @Bean
  public KongAdminClient kongAdminClient(KongConfigurationProperties configuration,
    Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract).encoder(encoder).decoder(decoder)
      .target(KongAdminClient.class, configuration.getUrl());
  }

  @Bean
  public KongGatewayService kongGatewayService(KongAdminClient kongAdminClient) {
    return new KongGatewayService(kongAdminClient);
  }
}
