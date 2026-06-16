package org.folio.am.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BootstrapCacheInProcessInvalidatorTest {

  @Mock private BootstrapCacheEvictor evictor;
  @InjectMocks private BootstrapCacheInProcessInvalidator invalidator;

  @AfterEach
  void cleanup() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void onDiscoveryCreate_evictsAfterCommit_whenTransactionActive() {
    TransactionSynchronizationManager.initSynchronization();

    invalidator.onDiscoveryCreate(new ModuleDiscovery().id("mod-foo-1.0.0"), ModuleType.BACKEND, "tok");

    // not evicted yet (still "in transaction")
    verify(evictor, never()).evictForModule("mod-foo-1.0.0");

    // simulate commit
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(evictor).evictForModule("mod-foo-1.0.0");
  }

  @Test
  void onDiscoveryUpdate_evictsAfterCommit_whenTransactionActive() {
    TransactionSynchronizationManager.initSynchronization();

    invalidator.onDiscoveryUpdate(new ModuleDiscovery().id("mod-foo-1.0.0"), ModuleType.BACKEND, "tok");

    verify(evictor, never()).evictForModule("mod-foo-1.0.0");

    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(evictor).evictForModule("mod-foo-1.0.0");
  }

  @Test
  void onDiscoveryDelete_evictsImmediately_whenNoTransaction() {
    invalidator.onDiscoveryDelete("mod-foo-1.0.0", "mod-foo-1.0.0", ModuleType.BACKEND, "tok");
    verify(evictor).evictForModule("mod-foo-1.0.0");
  }
}
