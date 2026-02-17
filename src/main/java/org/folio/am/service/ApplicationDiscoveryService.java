package org.folio.am.service;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDiscoveries;
import org.folio.am.domain.dto.ApplicationDiscovery;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ApplicationModuleDiscoveryEntity;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ApplicationRepository;
import org.folio.am.repository.ModuleDiscoveryRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.common.domain.model.OffsetRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationDiscoveryService {

  private final ModuleRepository repository;
  private final ModuleDiscoveryMapper mapper;
  private final ApplicationRepository applicationRepository;
  private final ModuleDiscoveryRepository discoveryRepository;

  @Transactional(readOnly = true)
  public ModuleDiscoveries get(String appId, Integer offset, Integer limit) {
    log.debug("Getting paged module discoveries for application: appId = {}, offset = {}, limit = {}",
      appId, offset, limit);

    var pageable = OffsetRequest.of(offset, limit);
    var mdEntities = repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(appId), pageable);
    var discoveries = mapItems(mdEntities.toList(), mapper::convert);

    return new ModuleDiscoveries().discovery(discoveries).totalRecords(mdEntities.getTotalElements());
  }

  @Transactional(readOnly = true)
  public ApplicationDiscoveries search(String query, Integer offset, Integer limit) {
    log.debug("Searching application discoveries: query = {}, offset = {}, limit = {}", query, offset, limit);

    var pageable = OffsetRequest.of(offset, limit);

    // 1. Query applications by CQL (pagination applies to applications)
    var applicationPage = isBlank(query)
      ? applicationRepository.findAll(pageable)
      : applicationRepository.findByCql(query, pageable);

    if (applicationPage.isEmpty()) {
      return applicationDiscoveries(emptyList(), 0);
    }

    // 2. Get application IDs from the page
    var appIds = applicationPage.map(ApplicationEntity::getId).getContent();

    // 3. Lightweight query: only (application_id, id, name, version, location) -- no ModuleDescriptor
    // 4. Group by application ID
    var appIdToDiscoveries = discoveryRepository.findAllWithApplicationIdByApplicationIdsIn(appIds).stream()
      .collect(Collectors.groupingBy(ApplicationModuleDiscoveryEntity::getApplicationId));

    var applicationDiscoveries = mapItems(appIdToDiscoveries.keySet(), toApplicationDiscovery(appIdToDiscoveries));

    return applicationDiscoveries(applicationDiscoveries, (int) applicationPage.getTotalElements());
  }

  private Function<String, ApplicationDiscovery> toApplicationDiscovery(
    Map<String, List<ApplicationModuleDiscoveryEntity>> appIdToDiscoveries) {
    return appId -> {
      var discoveries = mapItems(appIdToDiscoveries.get(appId), mapper::convert);

      return new ApplicationDiscovery()
        .applicationId(appId)
        .discovery(discoveries);
    };
  }

  private static ApplicationDiscoveries applicationDiscoveries(List<ApplicationDiscovery> applicationDiscoveries,
    int totalRecords) {
    return new ApplicationDiscoveries()
      .applicationDiscoveries(applicationDiscoveries)
      .totalRecords(totalRecords);
  }
}
