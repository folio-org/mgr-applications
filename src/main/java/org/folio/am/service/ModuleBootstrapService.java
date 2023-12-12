package org.folio.am.service;

import static org.folio.am.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.mapper.ModuleBootstrapMapper;
import org.folio.am.repository.ModuleBootstrapViewRepository;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.InterfaceReference;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModuleBootstrapService {
  private final ModuleBootstrapViewRepository repository;
  private final ModuleBootstrapMapper mapper;

  /**
   * Retrieves a module bootstrap data including information for the modules
   * that provides interfaces listed in required/optional section of the module descriptor.
   * Endpoint information is only returned for interfaces which the specified module actually uses.
   *
   * @param moduleId - the module identifier
   * @return Module bootstrap data
   */
  @Transactional(readOnly = true)
  public ModuleBootstrap getById(String moduleId) {
    var views = repository.findAllRequiredByModuleId(moduleId);

    var moduleView = removeModuleViewById(moduleId, views);
    var module = mapper.convert(moduleView);

    var requiredInterfaces = getRequiredOptionalInterfaces(moduleView);
    var requiredModules = toModuleDiscoveries(requiredInterfaces, views);

    return new ModuleBootstrap()
      .module(module)
      .requiredModules(requiredModules);
  }

  private ModuleBootstrapView removeModuleViewById(String moduleId, List<ModuleBootstrapView> result) {
    var view = result.stream().filter(m -> moduleId.equals(m.getId())).findFirst()
      .orElseThrow(() -> new EntityNotFoundException("Module not found by id: " + moduleId));

    result.remove(view);

    return view;
  }

  private static List<String> getRequiredOptionalInterfaces(ModuleBootstrapView moduleView) {
    var descriptor = moduleView.getDescriptor();
    return Stream.concat(toStream(descriptor.getRequires()), toStream(descriptor.getOptional()))
      .map(InterfaceReference::getId)
      .collect(Collectors.toList());
  }

  private List<ModuleBootstrapDiscovery> toModuleDiscoveries(List<String> requiredInterfaces,
    List<ModuleBootstrapView> moduleViews) {
    if (requiredInterfaces.isEmpty()) {
      return Collections.emptyList();
    }

    removeNotRequiredInterfaces(requiredInterfaces, moduleViews);

    return moduleViews.stream().map(mapper::convert).collect(Collectors.toList());
  }

  private static void removeNotRequiredInterfaces(List<String> requiredInterfaces, List<ModuleBootstrapView> views) {
    views.stream()
      .map(ModuleBootstrapView::getDescriptor)
      .map(ModuleDescriptor::getProvides)
      .filter(Objects::nonNull)
      .forEach(provides -> provides.removeIf(notRequiredInterface(requiredInterfaces)));
  }

  private static Predicate<InterfaceDescriptor> notRequiredInterface(List<String> requiredInterfaces) {
    return i -> !requiredInterfaces.contains(i.getId());
  }
}
