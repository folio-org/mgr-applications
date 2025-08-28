package org.folio.am.repository;

import static org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.model.ApplicationSlice;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

  @EntityGraph(attributePaths = {"modules", "uiModules"})
  @Query(value = "SELECT entity FROM ApplicationEntity entity WHERE entity.name = :name ORDER BY entity.id")
  @QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "50"))
  Stream<ApplicationEntity> streamByNameWithModules(@Param("name") String name);

  @Query(value = "SELECT a.id, a.name, a.version"
    + " FROM application a WHERE a.name = :name ORDER BY a.id", nativeQuery = true)
  @QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "100"))
  Stream<ApplicationSlice> streamByNameBasicFields(@Param("name") String name);

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
}
