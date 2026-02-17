package org.folio.am.repository;

import java.util.List;
import org.folio.am.domain.entity.ApplicationModuleDiscoveryEntity;
import org.folio.am.domain.entity.ModuleDiscoveryEntity;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleDiscoveryRepository extends JpaCqlRepository<ModuleDiscoveryEntity, String> {

  @Query(value = """
    SELECT DISTINCT m.id, m.version, m.name, m.discovery_url
      FROM module m
      INNER JOIN application_module am ON m.id = am.module_id
      WHERE m.discovery_url IS NOT NULL
        AND am.application_id IN :ids
      ORDER BY m.id
    """, nativeQuery = true)
  Page<ModuleDiscoveryEntity> findAllByApplicationIdsIn(@Param("ids") List<String> applicationIds, Pageable pageable);

  @Query(value = """
    SELECT DISTINCT am.application_id AS applicationId,
           m.id AS id, m.name AS name, m.version AS version,
           m.discovery_url AS location
      FROM module m
      INNER JOIN application_module am ON m.id = am.module_id
      WHERE m.discovery_url IS NOT NULL
        AND am.application_id IN :ids
      ORDER BY am.application_id, m.id
    """, nativeQuery = true)
  List<ApplicationModuleDiscoveryEntity> findAllWithApplicationIdByApplicationIdsIn(
    @Param("ids") List<String> applicationIds);
}
