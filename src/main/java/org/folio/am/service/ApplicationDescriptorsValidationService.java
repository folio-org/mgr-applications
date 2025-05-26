package org.folio.am.service;

import static org.folio.am.utils.CollectionUtils.union;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.mapper.ApplicationEntityMapper;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationDescriptorsValidationService {

  private final ApplicationService applicationService;
  private final ApplicationEntityMapper applicationEntityMapper;
  private final DependenciesValidator dependenciesValidator;

  public List<String> validateDescriptors(List<ApplicationDescriptor> descriptors) {
    log.info("validateDescriptors:: validate descriptors ids {}", getDescriptorIdsAsStr(descriptors));
    var applicationDescriptorsSet = new LinkedHashSet<>(descriptors);
    var dependencyQueue = applicationDescriptorsSet
      .stream()
      .map(ApplicationDescriptor::getDependencies)
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedList::new));
    var visited = new HashSet<>();
    while (!dependencyQueue.isEmpty()) {
      var dependency = dependencyQueue.poll();
      if (!visited.contains(dependency)) {
        var descriptorOpt = getByLatestDependencyVersion(dependency, applicationDescriptorsSet);
        descriptorOpt.ifPresent(descriptor -> {
          applicationDescriptorsSet.add(descriptor);
          dependencyQueue.addAll(descriptor.getDependencies());
          visited.add(dependency);
        });
      }
    }
    log.info("validateDescriptors:: validate applications including dependencies by ids {}",
      getDescriptorIdsAsStr(new ArrayList<>(applicationDescriptorsSet)));
    dependenciesValidator.validate(new ArrayList<>(applicationDescriptorsSet));
    return applicationDescriptorsSet
      .stream()
      .map(ApplicationDescriptor::getId)
      .toList();
  }

  private Optional<ApplicationDescriptor> getByLatestDependencyVersion(Dependency dependency,
    Set<ApplicationDescriptor> existDescriptors) {
    var requiredVersionRanges = RangesListFactory.create(dependency.getVersion());
    var descriptors = findApplicationDescriptorsByName(dependency.getName());
    var retrievedSatisfied = descriptors
      .stream()
      .filter(descriptor -> requiredVersionRanges.isSatisfiedBy(getSemver(descriptor.getVersion())))
      .toList();
    var existSatisfied = existDescriptors
      .stream()
      .filter(descriptor -> StringUtils.equals(descriptor.getName(), dependency.getName())
        && requiredVersionRanges.isSatisfiedBy(getSemver(descriptor.getVersion())))
      .toList();
    var unionDtos = union(retrievedSatisfied, existSatisfied);
    return unionDtos
      .stream()
      .max(Comparator.comparing(descriptor -> new Semver(descriptor.getVersion())));
  }

  private List<ApplicationDescriptor> findApplicationDescriptorsByName(String name) {
    return applicationService.findByNameWithModules(name)
      .stream()
      .map(applicationEntityMapper::convert)
      .toList();
  }

  private Semver getSemver(String version) {
    return new Semver(version);
  }

  private String getDescriptorIdsAsStr(List<ApplicationDescriptor> descriptors) {
    return descriptors
      .stream()
      .map(ApplicationDescriptor::getId)
      .collect(Collectors.joining(","));
  }
}
