package org.folio.am.mapper;

import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.dto.ModuleBootstrapEndpoint;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ModuleBootstrapMapper {

  @Mapping(target = "moduleId", source = "view.id")
  @Mapping(target = "interfaces", source = "view.descriptor.provides")
  ModuleBootstrapDiscovery convert(ModuleBootstrapView view);

  @Mapping(target = "endpoints", source = "descriptor.handlers")
  ModuleBootstrapInterface convert(InterfaceDescriptor descriptor);

  ModuleBootstrapEndpoint convert(RoutingEntry entry);
}
