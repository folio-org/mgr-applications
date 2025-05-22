package org.folio.am.mapper;

import static java.util.stream.Collectors.toCollection;
import static org.folio.am.utils.CollectionUtils.union;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.folio.am.domain.dto.ApplicationDto;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.common.domain.model.ModuleDescriptor;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ApplicationEntityToDtoMapper {

  @Mapping(target = "moduleDescriptors", expression = "java(addModuleDescriptors(applicationEntity))")
  @Mapping(target = "dependencies", expression = "java(addDependencies(applicationEntity))")
  ApplicationDto convert(ApplicationEntity applicationEntity);

  default List<ModuleDescriptor> addModuleDescriptors(ApplicationEntity applicationEntity) {
    var beModuleDescriptors = applicationEntity.getModules()
      .stream()
      .map(ModuleEntity::getDescriptor)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
    var uiModuleDescriptors = applicationEntity.getUiModules()
      .stream()
      .map(UiModuleEntity::getDescriptor)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
    return union(beModuleDescriptors, uiModuleDescriptors);
  }

  default List<Dependency> addDependencies(ApplicationEntity applicationEntity) {
    var applicationDescriptor = applicationEntity.getApplicationDescriptor();
    if (applicationDescriptor != null && applicationDescriptor.getDependencies() != null) {
      return applicationDescriptor.getDependencies();
    }
    return new ArrayList<>();
  }
}
