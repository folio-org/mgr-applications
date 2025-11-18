package org.folio.am.service;

import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.mapper.ModuleDiscoveryMapper;
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

  @Transactional(readOnly = true)
  public ModuleDiscoveries get(String appId, Integer offset, Integer limit) {
    log.debug("Getting paged module discoveries for application: appId = {}, offset = {}, limit = {}",
      appId, offset, limit);

    var pageable = OffsetRequest.of(offset, limit);
    var mdEntities = repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(appId), pageable);
    var discoveries = mapItems(mdEntities.toList(), mapper::convert);

    return new ModuleDiscoveries().discovery(discoveries).totalRecords(mdEntities.getTotalElements());
  }
}
