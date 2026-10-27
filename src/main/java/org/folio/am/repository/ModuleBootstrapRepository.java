package org.folio.am.repository;

import java.util.Collection;
import java.util.List;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.domain.model.EgressProviderRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleBootstrapRepository extends JpaRepository<ModuleBootstrapView, String> {

  /**
   * Queries the module and all its dependencies by the given id.
   *
   * <p>The {@code v_module_bootstrap} grain is one row per (module, application) — guaranteed by the
   * {@code application_module} composite primary key — and the nested {@code IN} subqueries are semi-joins that
   * cannot fan out rows, so no {@code DISTINCT} is needed. The entity id is the module id though, so Hibernate
   * returns one shared instance per module, populated from the first row it reads. Ordering by application id in
   * descending order makes that the row with the highest application id (compared as a string, unlike the egress
   * query, which compares versions), so {@code applicationId} in the response is stable.</p>
   *
   * @param moduleId the module identifier
   * @return List of module views
   */
  @Query(value = "SELECT view FROM ModuleBootstrapView view WHERE (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId) "
    + "ORDER BY view.applicationId DESC")
  List<ModuleBootstrapView> findAllRequiredByModuleId(@Param("moduleId") String moduleId);

  /**
   * Checks that the module is a BACKEND module of at least one application in the given scope.
   *
   * @param id the module identifier
   * @param applicationIds the application scope
   * @return {@code true} if the module belongs to at least one of the applications
   */
  boolean existsByIdAndApplicationIdIn(String id, Collection<String> applicationIds);

  /**
   * Returns the in-scope providers of the interfaces that the given module requires or optionally requires.
   *
   * <p>There is one row per (provider, in-scope application, required interface). The query selects scalar values,
   * not {@link ModuleBootstrapView} entities, so rows of the same module in different applications are not merged by
   * the persistence context. The module itself and providers without a discovery URL are excluded.</p>
   *
   * @param moduleId the requesting module id
   * @param applicationIds the application scope
   * @return provider rows, see {@link EgressProviderRow}
   */
  @Query("""
    SELECT new org.folio.am.domain.model.EgressProviderRow(
             v.id, v.applicationId, v.location, v.systemUserRequired, p.providedInterface)
      FROM ModuleBootstrapView v
      JOIN InterfaceReferenceEntity p ON p.moduleId = v.id AND p.type = 'PROVIDES'
     WHERE v.applicationId IN :applicationIds
       AND v.id <> :moduleId
       AND v.location IS NOT NULL
       AND p.providedInterface IS NOT NULL
       AND p.id IN (SELECT r.id FROM InterfaceReferenceEntity r
                     WHERE r.moduleId = :moduleId AND (r.type = 'REQUIRES' OR r.type = 'OPTIONAL'))
    """)
  List<EgressProviderRow> findEgressProviders(@Param("moduleId") String moduleId,
    @Param("applicationIds") Collection<String> applicationIds);

  /**
   * Returns the bootstrap view row(s) for a single module id. A list is returned (not Optional) because the same
   * module id can appear once per owning application.
   *
   * @param moduleId the module identifier
   * @return the module's own view row(s)
   */
  @Query(value = "SELECT view FROM ModuleBootstrapView view WHERE view.id = :moduleId")
  List<ModuleBootstrapView> findViewsById(@Param("moduleId") String moduleId);
}
