package org.folio.am.mapper;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.OPTIONAL;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.PROVIDES;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.REQUIRES;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.entity.InterfaceReferenceEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.ModuleType;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MappingMethods {

  public Set<ModuleEntity> mapModuleEntitiesFromAppDescriptor(ApplicationDescriptor descriptor) {
    return union(
      mapItemsToSet(descriptor.getModules(),
        module -> moduleToModuleEntity(module, moduleDescriptorsById(descriptor))),
      mapItemsToSet(descriptor.getUiModules(),
        module -> moduleToUiModuleEntity(module, uiModuleDescriptorsById(descriptor)))
    );
  }

  private ModuleEntity moduleToModuleEntity(Module module, Map<String, ModuleDescriptor> descriptorMap) {
    var entity = moduleEntityOf(module, ModuleType.BACKEND);

    var md = descriptorMap.get(entity.getId());
    if (md == null) {
      throw new IllegalArgumentException("Module descriptor not found for module with id: " + entity.getId());
    }

    entity.setDescriptor(md);

    populateInterfaceReferences(entity, md);

    return entity;
  }

  private ModuleEntity moduleToUiModuleEntity(Module module, Map<String, ModuleDescriptor> descriptorMap) {
    var entity = moduleEntityOf(module, ModuleType.UI);

    var md = descriptorMap.get(entity.getId());
    if (md == null) {
      throw new IllegalArgumentException("Module descriptor not found for ui module with id: " + entity.getId());
    }

    entity.setDescriptor(md);

    return entity;
  }

  private void populateInterfaceReferences(ModuleEntity entity, ModuleDescriptor md) {
    var provides = toInterfaceReferences(md.getProvides());
    var requires = md.getRequires();
    var optional = md.getOptional();

    var interfaces = new HashSet<InterfaceReferenceEntity>();
    interfaces.addAll(toInterfaceReferenceEntities(provides, PROVIDES));
    interfaces.addAll(toInterfaceReferenceEntities(requires, REQUIRES));
    interfaces.addAll(toInterfaceReferenceEntities(optional, OPTIONAL));

    entity.setInterfaces(interfaces);
  }

  private List<InterfaceReference> toInterfaceReferences(List<InterfaceDescriptor> descriptors) {
    return mapItems(descriptors, this::toInterfaceReference);
  }

  private InterfaceReference toInterfaceReference(InterfaceDescriptor descriptor) {
    return new InterfaceReference().id(descriptor.getId()).version(descriptor.getVersion());
  }

  private List<InterfaceReferenceEntity> toInterfaceReferenceEntities(Collection<InterfaceReference> refs,
    InterfaceReferenceEntity.ReferenceType type) {
    return mapItems(refs, r -> toInterfaceReferenceEntity(r, type));
  }

  private InterfaceReferenceEntity toInterfaceReferenceEntity(InterfaceReference ref,
    InterfaceReferenceEntity.ReferenceType type) {
    var e = new InterfaceReferenceEntity();
    e.setId(ref.getId());
    e.setVersion(ref.getVersion());
    e.setType(type);
    return e;
  }

  private static Map<String, ModuleDescriptor> moduleDescriptorsById(ApplicationDescriptor descriptor) {
    return toStream(descriptor.getModuleDescriptors())
      .collect(toMap(ModuleDescriptor::getId, identity()));
  }

  private static Map<String, ModuleDescriptor> uiModuleDescriptorsById(ApplicationDescriptor descriptor) {
    return toStream(descriptor.getUiModuleDescriptors())
      .collect(toMap(ModuleDescriptor::getId, identity()));
  }

  private static ModuleEntity moduleEntityOf(Module module, ModuleType moduleType) {
    var entity = new ModuleEntity();

    entity.setId(module.getId());
    entity.setName(module.getName());
    entity.setVersion(module.getVersion());
    entity.setType(moduleType);
    return entity;
  }

  private static <T> Set<T> union(Set<? extends T> setA, Set<? extends T> setB) {
    Objects.requireNonNull(setA, "First set is null");
    Objects.requireNonNull(setB, "Second set is null");

    Set<T> union = new HashSet<>(setA);
    union.addAll(setB);

    return union;
  }
}
