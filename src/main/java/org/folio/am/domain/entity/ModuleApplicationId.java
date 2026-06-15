package org.folio.am.domain.entity;

/**
 * Projection of a single {@code v_module_bootstrap} row, exposing the module id and the application
 * it belongs to. Unlike the {@link ModuleBootstrapView} entity (keyed on id), this projection is NOT
 * collapsed by {@code SELECT DISTINCT}, so a module shared across applications yields one row per
 * application — the full application-set needed for correct egress scoping.
 */
public interface ModuleApplicationId {

  String getId();

  String getApplicationId();
}
