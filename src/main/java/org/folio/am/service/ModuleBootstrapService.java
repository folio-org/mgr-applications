package org.folio.am.service;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.EgressBootstrapResult;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.mapper.ModuleBootstrapMapper;
import org.folio.am.service.ModuleBootstrapData.ResolvedModule;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.utils.SemverUtils;
import org.semver4j.Semver;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModuleBootstrapService {

  private final ModuleBootstrapDataProvider dataProvider;
  private final ModuleBootstrapMapper mapper;

  /**
   * Full module bootstrap: the module plus the highest-version providers of every interface it
   * requires/optional, across all applications.
   */
  public ModuleBootstrap getById(String moduleId) {
    var data = dataProvider.getData(moduleId);
    var self = requireSelf(data, moduleId);
    return new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), null))
      .requiredModules(requiredModules(self, data.providers(), null));
  }

  /**
   * Ingress bootstrap: this module's own routes only.
   */
  public ModuleBootstrap getIngressBootstrap(String moduleId) {
    var data = dataProvider.getData(moduleId);
    var self = requireSelf(data, moduleId);
    return new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), null))
      .requiredModules(List.of());
  }

  /**
   * Egress bootstrap scoped to the given applications. A module (self or provider) is in scope when any of its
   * owning applications is in the requested set, so a module shared across several applications is matched as long
   * as the tenant entitled at least one of them.
   */
  public EgressBootstrapResult getEgressBootstrap(String moduleId, List<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return new EgressBootstrapResult().found(false);
    }
    var scope = applicationIds.stream().filter(Objects::nonNull).collect(toUnmodifiableSet());
    if (scope.isEmpty()) {
      return new EgressBootstrapResult().found(false);
    }
    var data = dataProvider.getData(moduleId);
    var self = data.self();
    if (self == null || !inScope(self, scope)) {
      return new EgressBootstrapResult().found(false);
    }
    var providers = data.providers().stream()
      .filter(p -> inScope(p, scope))
      .toList();
    return new EgressBootstrapResult().found(true).bootstrap(new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), scope))
      .requiredModules(requiredModules(self, providers, scope)));
  }

  private List<ModuleBootstrapDiscovery> requiredModules(ResolvedModule self, List<ResolvedModule> providers,
    Set<String> scope) {
    var requiredInterfaceIds = requiredInterfaceIds(self);
    if (requiredInterfaceIds.isEmpty()) {
      return List.of();
    }
    var discoveries = providers.stream()
      .map(p -> toDiscovery(p, narrow(p, requiredInterfaceIds), scope))
      .toList();
    return deduplicate(discoveries);
  }

  private ModuleBootstrapDiscovery toDiscovery(ResolvedModule module, List<InterfaceDescriptor> provides,
    Set<String> scope) {
    return new ModuleBootstrapDiscovery()
      .moduleId(module.id())
      .applicationId(representativeApplicationId(module, scope))
      .location(module.location())
      .systemUserRequired(module.systemUserRequired())
      .interfaces(toStream(provides).map(mapper::convert).toList());
  }

  private static boolean inScope(ResolvedModule module, Set<String> scope) {
    return module.applicationIds().stream().anyMatch(scope::contains);
  }

  /**
   * The single applicationId reported on a discovery. When a scope is given (egress) the representative is the
   * lowest-sorted application that is both owned by the module and in scope, so the reported value is always one the
   * caller actually entitled; without a scope (ingress/full) it is the lowest-sorted owning application, which is
   * deterministic across cache reloads and replicas.
   */
  private static String representativeApplicationId(ResolvedModule module, Set<String> scope) {
    return module.applicationIds().stream()
      .filter(id -> scope == null || scope.contains(id))
      .min(Comparator.naturalOrder())
      .orElse(null);
  }

  private static List<InterfaceDescriptor> narrow(ResolvedModule provider, Set<String> requiredInterfaceIds) {
    return toStream(provider.descriptor().getProvides())
      .filter(i -> requiredInterfaceIds.contains(i.getId()))
      .toList();
  }

  private static Set<String> requiredInterfaceIds(ResolvedModule self) {
    var descriptor = self.descriptor();
    return Stream.concat(toStream(descriptor.getRequires()), toStream(descriptor.getOptional()))
      .map(InterfaceReference::getId)
      .collect(Collectors.toSet());
  }

  private static ResolvedModule requireSelf(ModuleBootstrapData data, String moduleId) {
    var self = data.self();
    if (self == null) {
      throw new EntityNotFoundException("Module not found by id: " + moduleId);
    }
    return self;
  }

  private static List<ModuleBootstrapDiscovery> deduplicate(List<ModuleBootstrapDiscovery> requiredModules) {
    var results = new HashMap<String, ModuleBootstrapDiscovery>();
    for (var discovery : requiredModules) {
      mergeHighestVersion(results, discovery);
    }
    return results.values().stream().toList();
  }

  private static void mergeHighestVersion(Map<String, ModuleBootstrapDiscovery> results,
    ModuleBootstrapDiscovery discovery) {
    // SemverUtils splits name from the full semver (handling pre-release/build suffixes like -SNAPSHOT, which a
    // naive last-dash split mangles into a different name and an unparseable version). A module id that is not
    // strict semver cannot be name/version-compared, so it is kept as last-seen rather than failing the whole
    // bootstrap (module ids are normally strict semver; this only guards against malformed persisted ids).
    String name;
    String version;
    try {
      name = SemverUtils.getName(discovery.getModuleId());
      version = SemverUtils.getVersion(discovery.getModuleId());
    } catch (RuntimeException e) {
      results.put(discovery.getModuleId(), discovery);
      return;
    }
    // Full semver precedence so the highest version wins deterministically regardless of list order, and a release
    // outranks the snapshot of the same version (2.0.0 > 2.0.0-SNAPSHOT). The legacy interface comparator returned
    // Integer.MAX_VALUE across differing majors, degrading "keep highest" to "keep last" (the query has no ORDER BY).
    results.merge(name, discovery, (existing, incoming) -> {
      var existingVersion = SemverUtils.getVersion(existing.getModuleId());
      return new Semver(version).isGreaterThan(new Semver(existingVersion)) ? incoming : existing;
    });
  }
}
