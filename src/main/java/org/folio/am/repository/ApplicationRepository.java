package org.folio.am.repository;

import java.util.List;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaCqlRepository<ApplicationEntity, String> {

  @Query(value = "SELECT entity FROM ApplicationEntity entity WHERE entity.id IN :ids")
  List<ApplicationEntity> findByIds(@Param("ids") List<String> applicationIds);

  @EntityGraph(attributePaths = {"modules", "uiModules"})
  @Query(value = "SELECT entity FROM ApplicationEntity entity WHERE entity.id IN :ids")
  List<ApplicationEntity> findByIdsWihModules(@Param("ids") List<String> applicationIds);

  @EntityGraph(attributePaths = {"modules", "uiModules"})
  @Query(value = "SELECT entity FROM ApplicationEntity entity WHERE entity.name = :name")
  List<ApplicationEntity> findByNameWithModules(String name);

  @Query(value = """
    SELECT DISTINCT entity FROM ApplicationEntity entity
      INNER JOIN entity.modules module
      WHERE module.id IN :ids
    """)
  List<ApplicationEntity> findApplicationsByModuleIds(@Param("ids") List<String> moduleIds);

  @Query(value = """
    SELECT CASE WHEN COUNT(app) > 0 THEN true ELSE false END
      FROM ApplicationEntity app
      INNER JOIN app.uiModules uiModule
      WHERE uiModule.id = :uiModuleId
        AND app.id <> :id
    """)
  boolean existsByNotIdAndUiModuleId(@Param("id") String id, @Param("uiModuleId") String uiModuleId);

  @Query(value = """
    SELECT CASE WHEN COUNT(app) > 0 THEN true ELSE false END
      FROM ApplicationEntity app
      INNER JOIN app.modules module
      WHERE module.id = :moduleId
        AND app.id <> :id
    """)
  boolean existsByNotIdAndModuleId(@Param("id") String id, @Param("moduleId") String moduleId);

  List<ApplicationEntity> findByName(String name);
}
