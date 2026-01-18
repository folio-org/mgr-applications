package org.folio.am.service;

import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;

/**
 * An application discovery event listener.
 */
public interface ApplicationDiscoveryListener {

  /**
   * Handles discovery create event.
   *
   * @param moduleDiscovery module discovery descriptor
   * @param type            module type
   * @param token           authentication token
   */
  default void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {}

  /**
   * Handles discovery update event.
   *
   * @param moduleDiscovery module discovery descriptor
   * @param type            module type
   * @param token           authentication token
   */
  default void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {}

  /**
   * Handles discovery delete event.
   *
   * @param serviceId  service id
   * @param instanceId instance id
   * @param type       module type
   * @param token      authentication token
   */
  default void onDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {}
}
