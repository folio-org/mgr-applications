package org.folio.am.integration.kong;

import static java.util.Collections.singletonList;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.service.ApplicationDiscoveryListener;
import org.folio.tools.kong.model.Service;
import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "application.kong.enabled")
public class KongDiscoveryListener implements ApplicationDiscoveryListener {

  private final KongGatewayService kongGatewayService;
  private final ModuleRepository moduleRepository;

  @Value("${routemanagement.enable:true}")
  private boolean routeManagementEnable = true;

  /**
   * Creates service into API Gateway.
   *
   * @param moduleDiscovery      module discovery descriptor
   * @param token                authentication token
   */
  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, String token) {
    log.info("Discovery information created for {}", moduleDiscovery.getId());
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
    log.info("Discovery information updated for {}", moduleDiscovery.getId());
    deleteServiceKongRoutes(moduleDiscovery.getId());
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
    log.info("Discovery information deleted for {} {}", serviceId, instanceId);
    deleteServiceKongRoutes(serviceId);
    kongGatewayService.deleteService(serviceId);
  }

  private void upsertService(ModuleDiscovery moduleDiscovery) {
    var serviceId = moduleDiscovery.getId();
    var service = new Service().name(serviceId).url(moduleDiscovery.getLocation());

    log.info("Upserting Kong service {}", serviceId);
    kongGatewayService.upsertService(service);

    if (routeManagementEnable) {
      var moduleEntity = moduleRepository.findById(moduleDiscovery.getArtifactId()).orElseThrow();
      log.info("Adding Kong service {} module {} routes", serviceId, moduleEntity.getId());
      kongGatewayService.addRoutes(null, singletonList(moduleEntity.getDescriptor()));
    } else {
      log.info("Kong routes management disabled for modules.");
    }
  }

  private void deleteServiceKongRoutes(String serviceId) {
    if (routeManagementEnable) {
      try {
        log.info("Deleting Kong service {}", serviceId);
        kongGatewayService.deleteServiceRoutes(serviceId);
      } catch (NoSuchElementException nse) {
        // Service doesn't exist - therefore no need to delete routes
        log.debug("Service doesn't exist: {}", serviceId);
      }
    }
  }
}
