package org.folio.am.service;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ApplicationEntityMapper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationReferencesValidationService {

  private final ApplicationService applicationService;
  private final ApplicationEntityMapper applicationEntityMapper;
  private final DependenciesValidator dependenciesValidator;

  public void validateReferences(ApplicationReferences applicationReferences) {
    var applicationEntities = applicationService
      .findByIdsWithModules(new ArrayList<>(applicationReferences.getApplicationIds()));
    var applicationDescriptors = mapItems(applicationEntities, applicationEntityMapper::convert);
    var foundIds = mapItemsToSet(applicationDescriptors, ApplicationDescriptor::getId);
    var notFoundIds = toStream(applicationReferences.getApplicationIds())
      .filter(not(foundIds::contains))
      .collect(joining(","));
    if (isNotEmpty(notFoundIds)) {
      var validationMessage = format("Applications not exist: ids = %s", notFoundIds);
      log.debug(validationMessage);
      throw new RequestValidationException(validationMessage);
    }
    log.debug("Validate applications: ids = {}", () -> join(",", foundIds));
    dependenciesValidator.validate(new ArrayList<>(applicationDescriptors));
  }
}
