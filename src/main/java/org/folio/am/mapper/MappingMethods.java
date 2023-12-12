package org.folio.am.mapper;

import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.OPTIONAL;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.PROVIDES;
import static org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType.REQUIRES;
import static org.folio.am.utils.CollectionUtils.mapItemsToSet;
import static org.folio.am.utils.CollectionUtils.toStream;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.entity.InterfaceReferenceEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.InterfaceReference;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MappingMethods {

  public Set<ModuleEntity> mapModuleEntitiesFromAppDescriptor(ApplicationDescriptor descriptor) {
    var modules = descriptor.getModules();

    if (isEmpty(modules)) {
      return emptySet();
    }

    var mdById = toStream(descriptor.getModuleDescriptors())
      .collect(Collectors.toMap(ModuleDescriptor::getId, Function.identity()));

    return mapItemsToSet(modules, module -> moduleToModuleEntity(module, mdById));
  }

  public List<UiModuleEntity> mapUiModuleEntitiesFromAppDescriptor(ApplicationDescriptor descriptor) {
    var modules = descriptor.getUiModules();

    if (isEmpty(modules)) {
      return Collections.emptyList();
    }

    var mdById = toStream(descriptor.getUiModuleDescriptors())
      .collect(Collectors.toMap(ModuleDescriptor::getId, Function.identity()));

    return mapItems(modules, module -> moduleToUiModuleEntity(module, mdById));
  }

  private ModuleEntity moduleToModuleEntity(Module module, Map<String, ModuleDescriptor> descriptorMap) {
    var entity = new ModuleEntity();

    entity.setId(module.getId());
    entity.setName(module.getName());
    entity.setVersion(module.getVersion());

    var md = descriptorMap.get(entity.getId());
    if (md == null) {
      throw new IllegalArgumentException("Module descriptor not found for module with id: " + entity.getId());
    }

    entity.setDescriptor(md);

    populateInterfaceReferences(entity, md);

    return entity;
  }

  private UiModuleEntity moduleToUiModuleEntity(Module module, Map<String, ModuleDescriptor> descriptorMap) {
    var entity = new UiModuleEntity();

    entity.setId(module.getId());
    entity.setName(module.getName());
    entity.setVersion(module.getVersion());

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
}
