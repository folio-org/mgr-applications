package org.folio.am.repository;

import org.folio.am.domain.entity.UiModuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UiModuleRepository extends JpaRepository<UiModuleEntity, String> {

}
