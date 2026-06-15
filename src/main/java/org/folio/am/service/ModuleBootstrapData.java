package org.folio.am.service;

import java.util.ArrayList;
import java.util.List;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.common.domain.model.ModuleDescriptor;

/**
 * Immutable, application-independent snapshot of a module's bootstrap resolution. Cached by module
 * id; ingress/full/egress are derived from it in memory. {@code self} is {@code null} when the module
 * has no {@code v_module_bootstrap} row (does not exist).
 */
public record ModuleBootstrapData(ResolvedModule self, List<ResolvedModule> providers) {

  /**
   * Builds the snapshot from the module-bootstrap view rows. Each module id belongs to exactly one
   * application, so the view yields one row per module id and {@code applicationId} is read directly.
   */
  public static ModuleBootstrapData from(String moduleId, List<ModuleBootstrapView> rows) {
    ResolvedModule self = null;
    var providers = new ArrayList<ResolvedModule>();
    for (var row : rows) {
      var resolved = new ResolvedModule(row.getId(), row.getLocation(), row.isSystemUserRequired(),
        row.getDescriptor(), row.getApplicationId());
      if (moduleId.equals(row.getId())) {
        self = resolved;
      } else {
        providers.add(resolved);
      }
    }
    return new ModuleBootstrapData(self, List.copyOf(providers));
  }

  /**
   * A resolved module row: module-level fields plus the single application the module belongs to.
   */
  public record ResolvedModule(String id, String location, boolean systemUserRequired,
                               ModuleDescriptor descriptor, String applicationId) {}
}
