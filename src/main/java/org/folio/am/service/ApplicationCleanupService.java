package org.folio.am.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationCleanupResult;
import org.folio.am.exception.ApplicationInstalledException;
import org.folio.am.integration.mte.EntitlementService;
import org.folio.am.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationCleanupService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationService applicationService;
  private final EntitlementService entitlementService;

  public ApplicationCleanupResult cleanup(String token) {
    ensureCleanupSupported();
    var ids = loadAllApplicationIds();
    var cleanedIds = new ArrayList<String>();
    var skippedIds = new ArrayList<String>();
    var failedIds = new ArrayList<String>();

    for (var id : ids) {
      cleanupApplication(id, token, cleanedIds, skippedIds, failedIds);
    }

    return buildResult(ids.size(), cleanedIds, skippedIds, failedIds);
  }

  private List<String> loadAllApplicationIds() {
    return applicationRepository.findAllApplicationIds();
  }

  private void cleanupApplication(String id, String token, ArrayList<String> cleanedIds,
    ArrayList<String> skippedIds, ArrayList<String> failedIds) {
    try {
      applicationService.delete(id, token);
      cleanedIds.add(id);
    } catch (ApplicationInstalledException e) {
      skippedIds.add(id);
      log.debug("Application is installed, skipping cleanup: id = {}", id);
    } catch (Exception e) {
      failedIds.add(id);
      log.warn("Failed to cleanup application: id = {}", id, e);
    }
  }

  private static ApplicationCleanupResult buildResult(int inspected, ArrayList<String> cleanedIds,
    ArrayList<String> skippedIds, ArrayList<String> failedIds) {
    return new ApplicationCleanupResult()
      .inspected(inspected)
      .cleaned(cleanedIds.size())
      .skipped(skippedIds.size())
      .failed(failedIds.size())
      .cleanedIds(cleanedIds)
      .skippedIds(skippedIds)
      .failedIds(failedIds);
  }

  private void ensureCleanupSupported() {
    if (entitlementService == null) {
      throw new UnsupportedOperationException(
        "Applications cleanup is not supported: entitlement service is not available");
    }
  }
}
