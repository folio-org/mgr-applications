package org.folio.am.mapper;

import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.entity.ApplicationEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR, uses = MappingMethods.class)
public interface ApplicationDescriptorMapper {

  /**
   * Converts {@link ApplicationDescriptor} to {@link ApplicationEntity} object.
   *
   * @param descriptor - {@link ApplicationDescriptor} object
   * @return converted {@link ApplicationEntity} object
   */
  @Mapping(target = "applicationDescriptor", expression = "java(removeModDescriptors(descriptor))")
  @Mapping(target = "modules", source = "descriptor")
  @Mapping(target = "uiModules", source = "descriptor")
  ApplicationEntity convert(ApplicationDescriptor descriptor);

  default ApplicationDescriptor removeModDescriptors(ApplicationDescriptor descriptor) {
    return descriptor.moduleDescriptors(null).uiModuleDescriptors(null);
  }
}
