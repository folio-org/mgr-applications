package org.folio.am.repository;

import java.util.List;
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
   * Same as {@link #findAllRequiredByModuleId(String)} but restricted to the given application scope: only the
   * module and provider rows whose application is in {@code applicationIds} are returned. When the module itself is
   * not in scope no self row is returned.
   *
   * @param moduleId the module identifier
   * @param applicationIds the application scope
   * @return List of in-scope module views
   */
  @Query(value = "SELECT DISTINCT view FROM ModuleBootstrapView view WHERE view.applicationId IN :applicationIds "
    + "AND (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId)")
  List<ModuleBootstrapView> findAllRequiredByModuleIdAndApplicationIdsIn(
    @Param("moduleId") String moduleId, @Param("applicationIds") List<String> applicationIds);

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
