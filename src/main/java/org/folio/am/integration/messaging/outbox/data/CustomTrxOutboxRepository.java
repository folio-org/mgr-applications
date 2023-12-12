package org.folio.am.integration.messaging.outbox.data;

import java.util.List;

public interface CustomTrxOutboxRepository {

  boolean isAnyData();

  List<TrxOutboxEntity> findAllOrderedByIdAndLimitedTo(int limit);
}
