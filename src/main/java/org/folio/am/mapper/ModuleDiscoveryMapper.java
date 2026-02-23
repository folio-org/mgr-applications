package org.folio.am.mapper;

import java.util.List;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ApplicationModuleDiscoveryProjection;
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
   * Converts {@link ModuleDiscoveryEntity} to {@link ModuleDiscovery} object.
   *
   * @param entity - {@link ModuleDiscoveryEntity} object
   * @return converted {@link ModuleDiscovery} object
   */
  ModuleDiscovery convert(ModuleDiscoveryEntity entity);

  /**
   * Converts {@link ApplicationModuleDiscoveryProjection} projection to {@link ModuleDiscovery} object.
   *
   * @param entity - {@link ApplicationModuleDiscoveryProjection} projection
   * @return converted {@link ModuleDiscovery} object
   */
  ModuleDiscovery convert(ApplicationModuleDiscoveryProjection entity);

  /**
   * Converts a {@link List} with {@link ModuleEntity} to a {@link List} with {@link ModuleDiscovery} objects.
   *
   * @param entities - {@link List} with {@link ModuleEntity} objects
   * @return converted {@link List} with {@link ModuleDiscovery} objects
   */
  List<ModuleDiscovery> convert(List<ModuleDiscoveryEntity> entities);
}
