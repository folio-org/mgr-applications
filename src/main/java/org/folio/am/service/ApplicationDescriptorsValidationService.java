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
import org.folio.am.domain.dto.ApplicationDto;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.mapper.ApplicationDescriptorToDtoMapper;
import org.folio.am.mapper.ApplicationEntityToDtoMapper;
import org.semver4j.RangesListFactory;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationDescriptorsValidationService {

  private final ApplicationService applicationService;
  private final ApplicationDescriptorToDtoMapper applicationDescriptorToDtoMapper;
  private final ApplicationEntityToDtoMapper applicationEntityToDtoMapper;
  private final DependenciesValidator dependenciesValidator;

  public List<String> validate(List<ApplicationDescriptor> descriptors) {
    var applicationDtos = descriptors
      .stream()
      .map(applicationDescriptorToDtoMapper::convert)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var dependencyQueue = applicationDtos
      .stream()
      .map(ApplicationDto::getDependencies)
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedList::new));
    var visited = new HashSet<>();
    while (!dependencyQueue.isEmpty()) {
      var dependency = dependencyQueue.poll();
      if (!visited.contains(dependency)) {
        var applicationDtoOpt = getByLatestDependencyVersion(dependency, applicationDtos);
        applicationDtoOpt.ifPresent(applicationDto -> {
          applicationDtos.add(applicationDto);
          dependencyQueue.addAll(applicationDto.getDependencies());
          visited.add(dependency);
        });
      }
    }
    dependenciesValidator.validate(new ArrayList<>(applicationDtos));
    return applicationDtos
      .stream()
      .map(ApplicationDto::getId)
      .toList();
  }

  private Optional<ApplicationDto> getByLatestDependencyVersion(Dependency dependency, Set<ApplicationDto> existDtos) {
    var requiredVersionRanges = RangesListFactory.create(dependency.getVersion());
    var dtos = findApplicationDtosByName(dependency.getName());
    var retrievedSatisfied = dtos
      .stream()
      .filter(dto -> requiredVersionRanges.isSatisfiedBy(dto.getSemver()))
      .toList();
    var existSatisfied = existDtos
      .stream()
      .filter(dto -> StringUtils.equals(dto.getName(), dependency.getName())
        && requiredVersionRanges.isSatisfiedBy(dto.getSemver()))
      .toList();
    var unionDtos = union(retrievedSatisfied, existSatisfied);
    return unionDtos
      .stream()
      .max(Comparator.comparing(ApplicationDto::getSemver));
  }

  private List<ApplicationDto> findApplicationDtosByName(String name) {
    return applicationService.findByName(name)
      .stream()
      .map(applicationEntityToDtoMapper::convert)
      .toList();
  }
}
