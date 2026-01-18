package org.folio.am.integration.okapi;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;

import feign.FeignException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.DeploymentDescriptor;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ArtifactEntity;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.service.ApplicationDescriptorListener;
import org.folio.am.service.ApplicationDiscoveryListener;
import org.folio.common.domain.model.ModuleDescriptor;

@Log4j2
@RequiredArgsConstructor
public class OkapiModuleRegisterService implements ApplicationDescriptorListener, ApplicationDiscoveryListener {

  private final OkapiClient okapiClient;
  private final ModuleRepository moduleRepository;

  /**
   * Creates module descriptors in Okapi.
   *
   * @param descriptor application descriptor
   * @param token      authentication token
   */
  @Override
  public void onDescriptorCreate(ApplicationDescriptor descriptor, String token) {
    var moduleDescriptors = descriptor.getModuleDescriptors();

    if (isNotEmpty(moduleDescriptors)) {
      log.debug("Module Descriptors to register in Okapi: {}", moduleDescriptors.size());

      okapiClient.createModuleDescriptors(moduleDescriptors, false, token);
      log.debug("Module registered in Okapi. Total count: {}", moduleDescriptors.size());
    }
  }

  /**
   * Deletes module descriptors from Okapi.
   *
   * @param descriptor application descriptor
   * @param token      authentication token
   */
  @Override
  public void onDescriptorDelete(ApplicationDescriptor descriptor, String token) {
    var moduleDescriptors = descriptor.getModuleDescriptors();

    if (isNotEmpty(moduleDescriptors)) {
      var foundModuleIds = findExistingModuleIds(moduleDescriptors);

      moduleDescriptors.stream()
        .filter(isModuleRemoved(foundModuleIds))
        .forEach(md -> deleteModuleDescriptor(md, token));

      log.debug("Related Module Descriptors removed from Okapi");
    }
  }

  /**
   * Creates module discovery in Okapi.
   *
   * @param discovery module discovery descriptor
   * @param type      module type
   * @param token     authentication token
   */
  @Override
  public void onDiscoveryCreate(ModuleDiscovery discovery, ModuleType type, String token) {
    upsertDiscovery(discovery, type, token);
  }

  /**
   * Updates module discovery in Okapi.
   *
   * @param discovery module discovery descriptor
   * @param type      module type
   * @param token     authentication token
   */
  @Override
  public void onDiscoveryUpdate(ModuleDiscovery discovery, ModuleType type, String token) {
    upsertDiscovery(discovery, type, token);
  }

  /**
   * Delete module discovery from Okapi.
   *
   * @param serviceId  service id
   * @param instanceId instance id
   * @param type       module type
   * @param token      authentication token
   */
  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {
    deleteDiscovery(serviceId, instanceId, type, token);
  }

  private Set<String> findExistingModuleIds(List<ModuleDescriptor> moduleDescriptors) {
    var moduleIds = mapItems(moduleDescriptors, ModuleDescriptor::getId);

    return mapItemsToSet(moduleRepository.findAllById(moduleIds), ArtifactEntity::getId);
  }

  private static Predicate<ModuleDescriptor> isModuleRemoved(Set<String> foundModuleIds) {
    return md -> !foundModuleIds.contains(md.getId());
  }

  private void deleteModuleDescriptor(ModuleDescriptor md, String token) {
    log.debug("Removing Module Descriptors: id = {}", md.getId());
    okapiClient.deleteModuleDescriptor(md.getId(), token);
  }

  private void upsertDiscovery(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    if (type == ModuleType.UI) {
      return;
    }

    try {
      var moduleId = moduleDiscovery.getId();
      var discoveryInfo = okapiClient.getDiscovery(moduleId, moduleId, token);
      if (Objects.equals(discoveryInfo.getUrl(), moduleDiscovery.getLocation())) {
        log.debug("Discovery info is not changed in Okapi: {}", moduleDiscovery);
        return;
      }

      updateDiscovery(moduleDiscovery, token);
    } catch (FeignException.NotFound e) {
      createDiscovery(moduleDiscovery, token);
    }
  }

  private void updateDiscovery(ModuleDiscovery moduleDiscovery, String token) {
    var moduleId = moduleDiscovery.getId();
    okapiClient.deleteDiscovery(moduleId, moduleId, token);
    okapiClient.createDiscovery(buildDeploymentDescriptor(moduleDiscovery), token);
    log.debug("Discovery info updated in Okapi: {}", moduleId);
  }

  private void createDiscovery(ModuleDiscovery moduleDiscovery, String token) {
    okapiClient.createDiscovery(buildDeploymentDescriptor(moduleDiscovery), token);
    log.debug("Discovery info registered in Okapi: {}", moduleDiscovery.getId());
  }

  private void deleteDiscovery(String serviceId, String instanceId, ModuleType type, String token) {
    if (type == ModuleType.UI) {
      return;
    }

    try {
      okapiClient.deleteDiscovery(serviceId, instanceId, token);
      log.debug("Discovery info removed from Okapi: {}", serviceId);
    } catch (FeignException.NotFound e) {
      log.debug("Discovery info is not found in Okapi: serviceId = {}, instanceId= {}", serviceId, instanceId);
    }
  }

  private static DeploymentDescriptor buildDeploymentDescriptor(ModuleDiscovery moduleDiscovery) {
    return new DeploymentDescriptor()
      .srvcId(moduleDiscovery.getId())
      .instId(moduleDiscovery.getId())
      .url(moduleDiscovery.getLocation());
  }
}
