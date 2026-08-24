package org.folio.am.service;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.SemverUtils.getVersion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ApplicationEntityMapper;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;
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
    var contextDescriptors = loadDependencyChain(applicationDescriptors);
    dependenciesValidator.validate(new ArrayList<>(applicationDescriptors), contextDescriptors);
  }

  private List<ApplicationDescriptor> loadDependencyChain(List<ApplicationDescriptor> targets) {
    var loadedNames = new HashSet<>(mapItemsToSet(targets, ApplicationDescriptor::getName));
    var context = new ArrayList<ApplicationDescriptor>();
    var queue = new ArrayDeque<Dependency>();
    collectUnseenDependencies(targets, loadedNames, queue);
    while (!queue.isEmpty()) {
      var depsToResolve = new ArrayList<>(queue);
      queue.clear();
      var idsByName = applicationService.findApplicationIdsByNames(mapItems(depsToResolve, Dependency::getName));
      var selectedIds = resolveLatestMatchingIds(depsToResolve, idsByName);
      if (selectedIds.isEmpty()) {
        continue;
      }
      var entities = applicationService.findByIdsWithModules(selectedIds);
      var descriptors = mapItems(entities, applicationEntityMapper::convert);
      context.addAll(descriptors);
      collectUnseenDependencies(descriptors, loadedNames, queue);
    }
    return context;
  }

  private static void collectUnseenDependencies(List<ApplicationDescriptor> source, Set<String> loadedNames,
    ArrayDeque<Dependency> queue) {
    toStream(source)
      .flatMap(appDescriptor -> toStream(appDescriptor.getDependencies()))
      .filter(dep -> !Boolean.TRUE.equals(dep.getOptional()))
      .filter(dep -> loadedNames.add(dep.getName()))
      .forEach(queue::add);
  }

  private static List<String> resolveLatestMatchingIds(List<Dependency> deps, Map<String, List<String>> idsByName) {
    var result = new ArrayList<String>();
    for (var dep : deps) {
      var range = RangesListFactory.create(dep.getVersion(), true);
      idsByName.getOrDefault(dep.getName(), List.of()).stream()
        .filter(id -> range.isSatisfiedBy(new Semver(getVersion(id))))
        .max(Comparator.comparing(id -> new Semver(getVersion(id))))
        .ifPresent(result::add);
    }
    return result;
  }
}
