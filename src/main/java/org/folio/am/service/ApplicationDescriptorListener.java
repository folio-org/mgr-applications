package org.folio.am.service;

import org.folio.am.domain.dto.ApplicationDescriptor;

/**
 * An application discovery event listener.
 */
public interface ApplicationDescriptorListener {

  /**
   * Handles application descriptor create event.
   *
   * @param descriptor application descriptor
   * @param token      authentication token
   */
  default void onDescriptorCreate(ApplicationDescriptor descriptor, String token) {
  }

  /**
   * Handles application descriptor delete event.
   *
   * @param descriptor application descriptor
   * @param token      authentication token
   */
  default void onDescriptorDelete(ApplicationDescriptor descriptor, String token) {
  }
}
