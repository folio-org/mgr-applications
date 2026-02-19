package org.folio.am.repository;

import org.folio.am.domain.entity.ApplicationDiscoveryView;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationDiscoveryRepository extends JpaCqlRepository<ApplicationDiscoveryView, String> {
}
