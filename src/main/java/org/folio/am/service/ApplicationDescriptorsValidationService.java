package org.folio.am.service;

import static java.lang.String.join;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.SemverUtils.getVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.semver4j.RangesList;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationDescriptorsValidationService {

  private final ApplicationService applicationService;
  private final DependenciesValidator dependenciesValidator;

  public List<String> validateDescriptors(List<ApplicationDescriptor> descriptors) {
    var applicationDescriptorsById = getApplicationDescriptorsById(descriptors);

    log.info("Validate descriptors: ids = {}", () -> join(", ", applicationDescriptorsById.keySet()));

    var resolvedDependencies = toStream(descriptors).collect(toMap(ApplicationDescriptor::getName, identity()));

    applicationDescriptorsById.values().stream()
      .flatMap(ad -> toStream(ad.getDependencies())).distinct()
      // app-i.dependency -> [app-dep-descriptor-1, ... , app-dep-descriptor-n]
      .flatMap(dependency -> resolveDependencyToAppDescriptors(dependency, resolvedDependencies))
      .forEach(dep -> applicationDescriptorsById.putIfAbsent(dep.getId(), dep));

    log.info("Validate applications including dependencies: ids = {}",
      getDescriptorIdsAsStr(applicationDescriptorsById.values()));
    dependenciesValidator.validate(new ArrayList<>(applicationDescriptorsById.values()));
    return mapItems(applicationDescriptorsById.values(), ApplicationDescriptor::getId);
  }

  private Stream<ApplicationDescriptor> resolveDependencyToAppDescriptors(Dependency dependency,
    Map<String, ApplicationDescriptor> resolvedDescriptorsByName) {
    if (resolvedDescriptorsByName.containsKey(dependency.getName())) {
      // ?? validate version range? -> if doesn't match, throw exception

      return Stream.of(resolvedDescriptorsByName.get(dependency.getName()));
    }

    var dependencyVersionRange = RangesListFactory.create(dependency.getVersion(), true);
    var latestAppId = applicationService.findAllApplicationIdsByName(dependency.getName())
      .filter(appVersionIsInRange(dependencyVersionRange))
      .max(Comparator.comparing(appId -> new Semver(getVersion(appId))));
    
    if (latestAppId.isPresent()) {
      var resolvedId = latestAppId.get();
      var resolvedDescriptor = applicationService.get(resolvedId, true);

      resolvedDescriptorsByName.put(resolvedDescriptor.getName(), resolvedDescriptor);

      var result = toStream(resolvedDescriptor.getDependencies())
        .flatMap(dep -> resolveDependencyToAppDescriptors(dep, resolvedDescriptorsByName));

      return Stream.concat(Stream.of(resolvedDescriptor), result);
    } else {
      // ?? throw exception?
      log.warn("Cannot resolve dependency: name = {}, version = {}", dependency.getName(), dependency.getVersion());
      return Stream.empty();
    }
  }

  private static Predicate<String> appVersionIsInRange(RangesList requiredVersionRanges) {
    return appId -> requiredVersionRanges.isSatisfiedBy(new Semver(getVersion(appId)));
  }

  private Map<String, ApplicationDescriptor> getApplicationDescriptorsById(List<ApplicationDescriptor> descriptors) {
    var result = new HashMap<String, ApplicationDescriptor>();

    for (var descriptor : emptyIfNull(descriptors)) {
      if (result.containsKey(descriptor.getId())) {
        throw new IllegalArgumentException(
          String.format("Duplicate application descriptor with id '%s' in the request", descriptor.getId()));
      }

      result.put(descriptor.getId(), descriptor);
    }
    return result;
  }

  private String getDescriptorIdsAsStr(Collection<ApplicationDescriptor> descriptors) {
    var applicationDescriptorIds = mapItems(descriptors, ApplicationDescriptor::getId);
    return join(",", applicationDescriptorIds);
  }
}
