package org.folio.am.mapper;

import java.util.List;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleDiscoveryEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ModuleDiscoveryMapper {

  /**
   * Converts {@link ModuleEntity} to {@link ModuleDiscovery} object.
   *
   * @param entity - {@link ModuleEntity} object
   * @return converted {@link ModuleDiscovery} object
   */
  @Mapping(target = "location", source = "entity.discoveryUrl")
  ModuleDiscovery convert(ModuleEntity entity);

  /**
   * Converts a {@link List} with {@link ModuleEntity} to a {@link List} with {@link ModuleDiscovery} objects.
   *
   * @param entities - {@link List} with {@link ModuleEntity} objects
   * @return converted {@link List} with {@link ModuleDiscovery} objects
   */
  List<ModuleDiscovery> convert(List<ModuleDiscoveryEntity> entities);

  @Mapping(target = "discoveryUrl", source = "moduleDiscovery.location")
  @Mapping(target = "applications", ignore = true)
  @Mapping(target = "descriptor", ignore = true)
  @Mapping(target = "interfaces", ignore = true)
  ModuleEntity convert(ModuleDiscovery moduleDiscovery);
}
