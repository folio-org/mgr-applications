package org.folio.am.service;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDto;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ApplicationEntityToDtoMapper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationReferencesValidationService {

  private final ApplicationService applicationService;
  private final ApplicationEntityToDtoMapper applicationEntityToDtoMapper;
  private final DependenciesValidator dependenciesValidator;

  public void validate(ApplicationReferences applicationReferences) {
    var applicationDtos = applicationService
      .findByIdsWithModules(new ArrayList<>(applicationReferences.getApplicationIds()))
      .stream()
      .map(applicationEntityToDtoMapper::convert)
      .toList();
    var foundIds = applicationDtos
      .stream()
      .map(ApplicationDto::getId)
      .collect(toSet());
    var notFoundIds = applicationReferences.getApplicationIds()
      .stream()
      .filter(not(foundIds::contains))
      .collect(joining(","));
    if (isNotEmpty(notFoundIds)) {
      var validationMessage = format("Applications not exist by ids : %s", notFoundIds);
      log.info("validate:: {}", validationMessage);
      throw new RequestValidationException(validationMessage);
    }
    log.info("validate:: validate applications ids {}", () -> join(",", foundIds));
    dependenciesValidator.validate(new ArrayList<>(applicationDtos));
  }
}
