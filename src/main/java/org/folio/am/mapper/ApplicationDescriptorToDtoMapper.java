package org.folio.am.mapper;

import java.util.ArrayList;
import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDto;
import org.folio.common.domain.model.ModuleDescriptor;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ApplicationDescriptorToDtoMapper {

  @Mapping(target = "moduleDescriptors", expression = "java(addModuleDescriptors(descriptor))")
  ApplicationDto convert(ApplicationDescriptor descriptor);

  default List<ModuleDescriptor> addModuleDescriptors(ApplicationDescriptor descriptor) {
    var moduleDescriptors = new ArrayList<ModuleDescriptor>();
    if (descriptor.getModuleDescriptors() != null) {
      moduleDescriptors.addAll(descriptor.getModuleDescriptors());
    }
    if (descriptor.getUiModuleDescriptors() != null) {
      moduleDescriptors.addAll(descriptor.getUiModuleDescriptors());
    }
    return moduleDescriptors;
  }
}
