package org.folio.am.repository;

import java.util.Optional;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleRepository extends JpaCqlRepository<ModuleEntity, String> {

  @Query(value = """
    SELECT module FROM ModuleEntity module
      WHERE module.discoveryUrl IS NOT NULL
        AND module.id = :id
    """)
  Optional<ModuleEntity> findByHasDiscoveryAndId(@Param("id") String id);
}
