package org.folio.am.service;

import static java.lang.String.join;
import static org.folio.am.utils.CollectionUtils.union;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;

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
    var dependencyQueue = toStream(applicationDescriptorsSet)
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
    return mapItems(applicationDescriptorsSet, ApplicationDescriptor::getId);
  }

  private Optional<ApplicationDescriptor> getByLatestDependencyVersion(Dependency dependency,
    Set<ApplicationDescriptor> existDescriptors) {
    var requiredVersionRanges = RangesListFactory.create(dependency.getVersion());
    var descriptors = findApplicationDescriptorsByName(dependency.getName());
    var retrievedSatisfied = toStream(descriptors)
      .filter(descriptor -> requiredVersionRanges.isSatisfiedBy(getSemver(descriptor.getVersion())))
      .toList();
    var existSatisfied = toStream(existDescriptors)
      .filter(descriptor -> StringUtils.equals(descriptor.getName(), dependency.getName())
        && requiredVersionRanges.isSatisfiedBy(getSemver(descriptor.getVersion())))
      .toList();
    var unionDescriptors = union(retrievedSatisfied, existSatisfied);
    return toStream(unionDescriptors)
      .max(Comparator.comparing(descriptor -> new Semver(descriptor.getVersion())));
  }

  private List<ApplicationDescriptor> findApplicationDescriptorsByName(String name) {
    return mapItems(applicationService.findByNameWithModules(name), applicationEntityMapper::convert);
  }

  private Semver getSemver(String version) {
    return new Semver(version);
  }

  private String getDescriptorIdsAsStr(List<ApplicationDescriptor> descriptors) {
    var applicationDescriptorIds = mapItems(descriptors, ApplicationDescriptor::getId);
    return join(",", applicationDescriptorIds);
  }
}
