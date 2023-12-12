package org.folio.am.integration.messaging.outbox.data;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrxOutboxRepository extends CrudRepository<TrxOutboxEntity, Long>, CustomTrxOutboxRepository {

  @Modifying(clearAutomatically = true)
  @Query(value = "DELETE FROM TrxOutboxEntity entity WHERE entity.id BETWEEN :lowerBound AND :highBound")
  void deleteAllByIdBetween(long lowerBound, long highBound);
}
