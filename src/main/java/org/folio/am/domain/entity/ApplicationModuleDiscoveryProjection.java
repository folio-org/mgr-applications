package org.folio.am.domain.entity;

/**
 * Projection interface for module discovery data with associated application ID.
 * Used to fetch minimal discovery data grouped by application without loading full entities.
 */
public interface ApplicationModuleDiscoveryProjection {

  /**
   * Gets the application identifier.
   *
   * @return the application ID
   */
  String getApplicationId();

  /**
   * Gets the module identifier.
   *
   * @return the module ID
   */
  String getId();

  /**
   * Gets the module name.
   *
   * @return the module name
   */
  String getName();

  /**
   * Gets the module version.
   *
   * @return the module version
   */
  String getVersion();

  /**
   * Gets the module discovery location URL.
   *
   * @return the discovery location
   */
  String getLocation();
}
