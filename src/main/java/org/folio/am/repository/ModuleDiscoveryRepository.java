package org.folio.am.repository;

import org.folio.am.domain.entity.ModuleDiscoveryEntity;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleDiscoveryRepository extends JpaCqlRepository<ModuleDiscoveryEntity, String> {}
