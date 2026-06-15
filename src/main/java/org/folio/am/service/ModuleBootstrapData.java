package org.folio.am.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.am.domain.entity.ModuleApplicationId;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.common.domain.model.ModuleDescriptor;

/**
 * Immutable, application-independent snapshot of a module's bootstrap resolution. Cached by module
 * id; ingress/full/egress are derived from it in memory. {@code self} is {@code null} when the module
 * has no {@code v_module_bootstrap} row (does not exist).
 */
public record ModuleBootstrapData(ResolvedModule self, List<ResolvedModule> providers) {

  /**
   * A resolved module row group: module-level fields (identical across the module's applications)
   * plus the full set of applications the module belongs to.
   */
  public record ResolvedModule(String id, String location, boolean systemUserRequired,
                               ModuleDescriptor descriptor, Set<String> applicationIds) {}

  /**
   * Builds the snapshot from the (distinct) entity rows (descriptors) and the (un-collapsed)
   * (id, applicationId) projection rows (application sets).
   */
  public static ModuleBootstrapData from(String moduleId, List<ModuleBootstrapView> rows,
    List<ModuleApplicationId> appIdRows) {
    Map<String, Set<String>> appIdsById = appIdRows.stream().collect(groupingBy(
      ModuleApplicationId::getId, mapping(ModuleApplicationId::getApplicationId, toUnmodifiableSet())));

    ResolvedModule self = null;
    var providers = new ArrayList<ResolvedModule>();
    for (var row : rows) {
      var resolved = new ResolvedModule(row.getId(), row.getLocation(), row.isSystemUserRequired(),
        row.getDescriptor(), appIdsById.getOrDefault(row.getId(), Set.of()));
      if (moduleId.equals(row.getId())) {
        self = resolved;
      } else {
        providers.add(resolved);
      }
    }
    return new ModuleBootstrapData(self, List.copyOf(providers));
  }
}
