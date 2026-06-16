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
   * Returns the ids of all modules whose cached bootstrap snapshot can change when the discovery of
   * {@code moduleId} changes — i.e. modules that require/optionally-use an interface provided by
   * {@code moduleId} (the reverse of {@link #findAllRequiredByModuleId}). Used to scope cache
   * invalidation to the affected fan-out instead of flushing the whole cache.
   *
   * @param moduleId the module whose discovery changed
   * @return distinct dependent module ids (excluding {@code moduleId} itself)
   */
  @Query(value = "SELECT DISTINCT r.moduleId FROM InterfaceReferenceEntity r WHERE "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL') AND "
    + "r.id IN (SELECT p.id FROM InterfaceReferenceEntity p WHERE p.moduleId = :moduleId AND p.type = 'PROVIDES')")
  List<String> findAllDependentModuleIds(@Param("moduleId") String moduleId);
}
