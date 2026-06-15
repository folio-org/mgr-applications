package org.folio.am.service;

import static org.folio.am.utils.ModuleIdUtils.getNameAndVersion;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
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
      .module(toDiscovery(self, self.descriptor().getProvides()))
      .requiredModules(requiredModules(self, data.providers()));
  }

  /**
   * Ingress bootstrap: this module's own routes only.
   */
  public ModuleBootstrap getIngressBootstrap(String moduleId) {
    var data = dataProvider.getData(moduleId);
    var self = requireSelf(data, moduleId);
    return new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides()))
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
    if (self == null || !scope.contains(self.applicationId())) {
      return new EgressBootstrapResult().found(false);
    }
    var providers = data.providers().stream()
      .filter(p -> scope.contains(p.applicationId()))
      .toList();
    return new EgressBootstrapResult().found(true).bootstrap(new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides()))
      .requiredModules(requiredModules(self, providers)));
  }

  private List<ModuleBootstrapDiscovery> requiredModules(ResolvedModule self, List<ResolvedModule> providers) {
    var requiredInterfaceIds = requiredInterfaceIds(self);
    if (requiredInterfaceIds.isEmpty()) {
      return List.of();
    }
    var discoveries = providers.stream()
      .map(p -> toDiscovery(p, narrow(p, requiredInterfaceIds)))
      .collect(Collectors.toList());
    return deduplicate(discoveries);
  }

  private ModuleBootstrapDiscovery toDiscovery(ResolvedModule module, List<InterfaceDescriptor> provides) {
    return new ModuleBootstrapDiscovery()
      .moduleId(module.id())
      .applicationId(module.applicationId())
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
