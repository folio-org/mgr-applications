package org.folio.am.integration.okapi;

import lombok.extern.log4j.Log4j2;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Log4j2
@Configuration
@ConditionalOnFarModeDisabled
@ConditionalOnProperty("application.okapi.enabled")
public class OkapiConfiguration {

  @Bean
  public OkapiClient okapiClient(OkapiConfigurationProperties configuration) {
    var restClient = RestClient.builder().baseUrl(configuration.getUrl()).build();
    var factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    return factory.createClient(OkapiClient.class);
  }

  @Bean
  public OkapiModuleRegisterService okapiListener(OkapiClient okapiClient, ModuleRepository moduleRepository) {
    return new OkapiModuleRegisterService(okapiClient, moduleRepository);
  }
}
