package org.folio.am.integration.messaging.outbox.data;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomTrxOutboxRepositoryImpl implements CustomTrxOutboxRepository {

  private final EntityManager em;

  @Override
  public boolean isAnyData() {
    try {
      em.createQuery("SELECT 1 FROM TrxOutboxEntity", Integer.class)
        .setMaxResults(1)
        .getSingleResult();
      return true;
    } catch (NoResultException e) {
      return false;
    }
  }

  @Override
  public List<TrxOutboxEntity> findAllOrderedByIdAndLimitedTo(int limit) {
    return em.createQuery("SELECT entity FROM TrxOutboxEntity entity ORDER BY entity.id ASC", TrxOutboxEntity.class)
      .setMaxResults(limit).getResultList();
  }
}
