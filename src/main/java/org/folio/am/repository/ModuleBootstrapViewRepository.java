package org.folio.am.repository;

import java.util.List;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleBootstrapViewRepository extends JpaRepository<ModuleBootstrapView, String> {

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
}
