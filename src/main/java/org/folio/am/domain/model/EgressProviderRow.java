package org.folio.am.domain.model;

import org.folio.common.domain.model.InterfaceDescriptor;

/**
 * One row of the egress bootstrap query: a provider module, one in-scope application that contains it, and one of
 * its provided interfaces that the requesting module requires or optionally requires.
 *
 * @param moduleId - provider module id
 * @param applicationId - in-scope application that contains the provider
 * @param location - provider discovery URL
 * @param systemUserRequired - whether the provider requires a system user
 * @param providedInterface - the provided interface, read from {@code module_interface_reference.provided_interface}
 */
public record EgressProviderRow(String moduleId, String applicationId, String location, boolean systemUserRequired,
  InterfaceDescriptor providedInterface) {}
