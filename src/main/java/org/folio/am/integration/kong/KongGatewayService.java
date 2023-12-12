package org.folio.am.integration.kong;

import feign.FeignException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.integration.kong.model.KongService;
import org.folio.am.service.ApplicationDiscoveryListener;

@Log4j2
@RequiredArgsConstructor
public class KongGatewayService implements ApplicationDiscoveryListener {

  private final KongAdminClient kongAdminClient;

  /**
   * Creates service into API Gateway.
   *
   * @param moduleDiscovery      module discovery descriptor
   * @param token                authentication token
   */
  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, String token) {
    upsertService(moduleDiscovery);
  }

  /**
   * Updates service in API Gateway.
   *
   * @param moduleDiscovery      module discovery descriptor
   * @param token                authentication token
   */
  @Override
  public void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, String token) {
    upsertService(moduleDiscovery);
  }

  /**
   * Delete service from API Gateway.
   *
   * @param serviceId  service id
   * @param instanceId instance id
   * @param token      authentication token
   */
  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, String token) {
    kongAdminClient.deleteService(serviceId);
    log.debug("discovery info removed from Kong");
  }

  private void upsertService(ModuleDiscovery moduleDiscovery) {
    var serviceId = moduleDiscovery.getId();
    var service = new KongService().name(serviceId).url(moduleDiscovery.getLocation());

    try {
      var kongService = kongAdminClient.getService(serviceId);
      var kongUrl = getUrl(kongService);
      if (!Objects.equals(kongUrl, service.getUrl())) {
        log.debug("Module discovery updated in Kong: {}", serviceId);
        kongAdminClient.upsertService(serviceId, service);
      } else {
        log.debug("Module discovery with id {} not changed in Kong", serviceId);
      }
    } catch (FeignException.NotFound e) {
      kongAdminClient.upsertService(serviceId, service);
      log.debug("Module discovery created in Kong: {}", serviceId);
    }
  }

  private String getUrl(KongService kongService) {
    if (kongService.getUrl() != null) {
      return kongService.getUrl();
    }
    return String.format("%s://%s:%s", kongService.getProtocol(), kongService.getHost(), kongService.getPort());
  }
}
