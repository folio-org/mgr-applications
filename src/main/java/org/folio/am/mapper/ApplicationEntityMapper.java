package org.folio.am.mapper;

import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.ArrayList;
import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.common.domain.model.ModuleDescriptor;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ApplicationEntityMapper {

  @Mapping(target = "moduleDescriptors", expression = "java(addModuleDescriptors(applicationEntity))")
  @Mapping(target = "uiModuleDescriptors", expression = "java(addUiModuleDescriptors(applicationEntity))")
  @Mapping(target = "dependencies", expression = "java(addDependencies(applicationEntity))")
  @Mapping(target = "modules", ignore = true)
  @Mapping(target = "uiModules", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "metadata", ignore = true)
  @Mapping(target = "platform", ignore = true)
  @Mapping(target = "deployment", ignore = true)
  ApplicationDescriptor convert(ApplicationEntity applicationEntity);

  default List<ModuleDescriptor> addModuleDescriptors(ApplicationEntity applicationEntity) {
    return mapItems(applicationEntity.getModules(), ModuleEntity::getDescriptor);
  }

  default List<ModuleDescriptor> addUiModuleDescriptors(ApplicationEntity applicationEntity) {
    return mapItems(applicationEntity.getUiModules(), UiModuleEntity::getDescriptor);
  }

  default List<Dependency> addDependencies(ApplicationEntity applicationEntity) {
    var applicationDescriptor = applicationEntity.getApplicationDescriptor();
    if (applicationDescriptor != null && applicationDescriptor.getDependencies() != null) {
      return applicationDescriptor.getDependencies();
    }
    return new ArrayList<>();
  }
}
