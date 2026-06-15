package org.folio.am.service;

import static org.folio.am.utils.ModuleIdUtils.getNameAndVersion;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
import org.folio.common.utils.InterfaceComparisonUtils;
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
   * Egress bootstrap scoped to the given applications.
   */
  public EgressBootstrapResult getEgressBootstrap(String moduleId, List<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return new EgressBootstrapResult().found(false);
    }
    var scope = Set.copyOf(applicationIds);
    var data = dataProvider.getData(moduleId);
    var self = data.self();
    if (self == null || disjoint(self.applicationIds(), scope)) {
      return new EgressBootstrapResult().found(false);
    }
    var providers = data.providers().stream()
      .filter(p -> !disjoint(p.applicationIds(), scope))
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
      .collect(Collectors.toList());
    return deduplicate(discoveries);
  }

  private ModuleBootstrapDiscovery toDiscovery(ResolvedModule module, List<InterfaceDescriptor> provides,
    Set<String> scope) {
    return new ModuleBootstrapDiscovery()
      .moduleId(module.id())
      .applicationId(representativeApp(module, scope))
      .location(module.location())
      .systemUserRequired(module.systemUserRequired())
      .interfaces(toStream(provides).map(mapper::convert).collect(Collectors.toList()));
  }

  private static List<InterfaceDescriptor> narrow(ResolvedModule provider, Set<String> requiredInterfaceIds) {
    return toStream(provider.descriptor().getProvides())
      .filter(i -> requiredInterfaceIds.contains(i.getId()))
      .collect(Collectors.toList());
  }

  private static Set<String> requiredInterfaceIds(ResolvedModule self) {
    var descriptor = self.descriptor();
    return Stream.concat(toStream(descriptor.getRequires()), toStream(descriptor.getOptional()))
      .map(InterfaceReference::getId)
      .collect(Collectors.toSet());
  }

  private static String representativeApp(ResolvedModule module, Set<String> scope) {
    return module.applicationIds().stream()
      .filter(app -> scope == null || scope.contains(app))
      .min(Comparator.naturalOrder())
      .orElse(null);
  }

  private static boolean disjoint(Set<String> a, Set<String> b) {
    return Collections.disjoint(a, b);
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
      var nameAndVersion = getNameAndVersion(discovery.getModuleId());
      var name = nameAndVersion.getLeft();
      var version = nameAndVersion.getRight();
      var existing = results.get(name);
      if (existing == null) {
        results.put(name, discovery);
      } else {
        var existingVersion = getNameAndVersion(existing.getModuleId()).getRight();
        if (InterfaceComparisonUtils.compare("", version, "", existingVersion) > 0) {
          results.put(name, discovery);
        }
      }
    }
    return results.values().stream().toList();
  }
}
