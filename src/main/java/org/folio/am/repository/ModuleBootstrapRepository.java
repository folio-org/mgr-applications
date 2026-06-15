package org.folio.am.repository;

import java.util.List;
import org.folio.am.domain.entity.ModuleApplicationId;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleBootstrapRepository extends JpaRepository<ModuleBootstrapView, String> {

  /**
   * Queries the module and all its dependencies by the given id.
   *
   * @param moduleId the module identifier
   * @return List of module views
   */
  @Query(value = "SELECT DISTINCT view FROM ModuleBootstrapView view WHERE (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId)")
  List<ModuleBootstrapView> findAllRequiredByModuleId(@Param("moduleId") String moduleId);

  /**
   * Queries the module and all its dependencies by the given id, restricted to modules
   * belonging to the given applications.
   *
   * @param moduleId       the module identifier
   * @param applicationIds the list of application IDs to restrict the query to
   * @return List of module views
   */
  @Query(value = "SELECT DISTINCT view FROM ModuleBootstrapView view WHERE (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId) "
    + "and view.applicationId IN :applicationIds")
  List<ModuleBootstrapView> findAllRequiredByModuleIdInApplications(@Param("moduleId") String moduleId,
    @Param("applicationIds") List<String> applicationIds);

  /**
   * Returns one (moduleId, applicationId) row per (module, application) pair for the module and all
   * its required-interface providers — same selection as {@link #findAllRequiredByModuleId(String)},
   * but WITHOUT the entity {@code DISTINCT} collapse, so shared modules expose their full app-set.
   *
   * @param moduleId the module identifier
   * @return list of (id, applicationId) projections
   */
  @Query(value = "SELECT view.id AS id, view.applicationId AS applicationId FROM ModuleBootstrapView view "
    + "WHERE (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId)")
  List<ModuleApplicationId> findApplicationIdsByModuleId(@Param("moduleId") String moduleId);
}
