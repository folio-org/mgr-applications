package org.folio.am.service;

import org.folio.am.domain.dto.ModuleDiscovery;

/**
 * An application discovery event listener.
 */
public interface ApplicationDiscoveryListener {

  /**
   * Handles discovery create event.
   *
   * @param moduleDiscovery module discovery descriptor
   * @param token                authentication token
   */
  default void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, String token) {}

  /**
   * Handles discovery update event.
   *
   * @param moduleDiscovery module discovery descriptor
   * @param token                authentication token
   */
  default void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, String token) {}

  /**
   * Handles discovery delete event.
   *
   * @param serviceId  service id
   * @param instanceId instance id
   * @param token      authentication token
   */
  default void onDiscoveryDelete(String serviceId, String instanceId, String token) {}
}
