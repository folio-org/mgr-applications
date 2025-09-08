package org.folio.am.integration.okapi;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.extern.log4j.Log4j2;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Log4j2
@Configuration
@Import(FeignClientsConfiguration.class)
@ConditionalOnFarModeDisabled
@ConditionalOnProperty("application.okapi.enabled")
public class OkapiConfiguration {

  @Bean
  public OkapiClient okapiClient(OkapiConfigurationProperties configuration,
    Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract).encoder(encoder).decoder(decoder)
      .target(OkapiClient.class, configuration.getUrl());
  }

  @Bean
  public OkapiModuleRegisterService okapiListener(OkapiClient okapiClient, ModuleRepository moduleRepository) {
    return new OkapiModuleRegisterService(okapiClient, moduleRepository);
  }
}
