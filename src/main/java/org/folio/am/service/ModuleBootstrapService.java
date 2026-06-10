package org.folio.am.service;

import static org.folio.am.utils.ModuleIdUtils.getNameAndVersion;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.mapper.ModuleBootstrapMapper;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.utils.InterfaceComparisonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModuleBootstrapService {

  private final ModuleBootstrapRepository repository;
  private final ModuleBootstrapMapper mapper;
  private final ApplicationClosureResolver applicationClosureResolver;

  /**
   * Retrieves a module bootstrap data including information for the modules that provides interfaces listed in
   * required/optional section of the module descriptor. Endpoint information is only returned for interfaces which the
   * specified module actually uses.
   *
   * @param moduleId - the module identifier
   * @return Module bootstrap data
   */
  @Transactional(readOnly = true)
  public ModuleBootstrap getById(String moduleId) {
    return getById(moduleId, null);
  }

  /**
   * Retrieves a module bootstrap data scoped to an application closure when {@code applicationId} is provided,
   * or falls back to the legacy all-applications query when it is {@code null}.
   *
   * @param moduleId      - the module identifier
   * @param applicationId - optional application identifier; when non-null, providers are restricted to the
   *                        transitive closure of the application's dependencies
   * @return Module bootstrap data
   */
  @Transactional(readOnly = true)
  public ModuleBootstrap getById(String moduleId, String applicationId) {
    var views = applicationId == null
      ? repository.findAllRequiredByModuleId(moduleId)
      : repository.findAllRequiredByModuleIdAndApplicationIds(
          moduleId, applicationClosureResolver.resolve(Set.of(applicationId)));

    var moduleView = removeModuleViewById(moduleId, views);
    var module = mapper.convert(moduleView);

    var requiredInterfaces = getRequiredOptionalInterfaces(moduleView);
    var requiredModules = toModuleDiscoveries(requiredInterfaces, views);

    var preferred = applicationId == null ? Set.<String>of() : Set.of(applicationId);
    requiredModules = deduplicateRequiredModules(requiredModules, preferred);

    return new ModuleBootstrap()
      .module(module)
      .requiredModules(requiredModules);
  }

  private List<ModuleBootstrapDiscovery> deduplicateRequiredModules(
      List<ModuleBootstrapDiscovery> requiredModules,
      Set<String> preferredApplicationIds) {
    var results = new HashMap<String, ModuleBootstrapDiscovery>();

    for (var candidate : requiredModules) {
      var moduleName = getNameAndVersion(candidate.getModuleId()).getLeft();
      var existing = results.get(moduleName);
      if (existing == null || isBetterCandidate(candidate, existing, preferredApplicationIds)) {
        results.put(moduleName, candidate);
      }
    }

    return results.values().stream().toList();
  }

  private boolean isBetterCandidate(ModuleBootstrapDiscovery candidate, ModuleBootstrapDiscovery existing,
      Set<String> preferredApplicationIds) {
    var candidatePreferred = preferredApplicationIds.contains(candidate.getApplicationId());
    var existingPreferred = preferredApplicationIds.contains(existing.getApplicationId());
    if (candidatePreferred != existingPreferred) {
      return candidatePreferred;
    }
    var candidateVersion = getNameAndVersion(candidate.getModuleId()).getRight();
    var existingVersion = getNameAndVersion(existing.getModuleId()).getRight();
    return InterfaceComparisonUtils.compare("", candidateVersion, "", existingVersion) > 0;
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
