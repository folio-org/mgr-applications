package org.folio.am.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.am.utils.CollectionUtils.union;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class DependenciesValidator {

  public void validate(List<ApplicationDescriptor> applicationDescriptors) {
    validateDependencies(applicationDescriptors);
    validateInterfaces(applicationDescriptors);
  }

  void validateDependencies(List<ApplicationDescriptor> applicationDescriptors) {
    var appNamesWithSeveralVersions = applicationDescriptors
      .stream()
      .collect(groupingBy(ApplicationDescriptor::getName, mapping(ApplicationDescriptor::getVersion, toSet())))
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().size() > 1)
      .map(Entry::getKey)
      .collect(joining(";"));
    if (isNotEmpty(appNamesWithSeveralVersions)) {
      var parameter = new Parameter().key("applicationNames").value(appNamesWithSeveralVersions);
      var validationMessage = "Used same applications with different versions";
      log.info("validateApplications:: {}", validationMessage + " " + appNamesWithSeveralVersions);
      throw new RequestValidationException(validationMessage, List.of(parameter));
    }
    var mapApplicationNameToVersions = applicationDescriptors
      .stream()
      .collect(toMap(ApplicationDescriptor::getName, ApplicationDescriptor::getVersion));
    for (var applicationDto : applicationDescriptors) {
      var dependencies = applicationDto.getDependencies();
      validateApplicationDependencies(dependencies, mapApplicationNameToVersions);
    }
  }

  void validateInterfaces(List<ApplicationDescriptor> applicationDescriptors) {
    var provided = getProvidedInterfaces(applicationDescriptors);
    var missedInterfacesPerApplication = applicationDescriptors
      .stream()
      .collect(toMap(ApplicationDescriptor::getId, applicationDescriptor -> {
        var neededInterfaces = getRequiredInterfaces(applicationDescriptor);
        neededInterfaces.removeIf(interfaceToCheck -> provided
          .stream().anyMatch(pr -> StringUtils.equals(pr.getId(), interfaceToCheck.getId())
            && contains(interfaceToCheck.getVersion(), pr.getVersion())));
        return interfaceReferencesAsString(neededInterfaces);
      }));
    var errorParameters = new ArrayList<Parameter>();
    for (var entry : missedInterfacesPerApplication.entrySet()) {
      if (isNotEmpty(entry.getValue())) {
        errorParameters.add(new Parameter().key(entry.getKey()).value(entry.getValue()));
      }
    }
    if (!errorParameters.isEmpty()) {
      throw new RequestValidationException("Missing interfaces found for the applications", errorParameters);
    }
  }

  private void validateApplicationDependencies(List<Dependency> dependencies,
    Map<String, String> mapApplicationNameToVersions) {
    for (var dependency : dependencies) {
      if (Boolean.TRUE.equals(dependency.getOptional())) {
        log.debug("validateApplicationDependencies:: Skipping validation for optional dependency: {}",
          dependency.getName());
        continue;
      }
      if (!mapApplicationNameToVersions.containsKey(dependency.getName())) {
        var validationMessage = format("Application dependency by name %s not exist", dependency.getName());
        log.info("validateApplicationDependencies:: {}", validationMessage);
        throw new RequestValidationException(validationMessage);
      }
      var existVersion = mapApplicationNameToVersions.get(dependency.getName());
      var requiredVersionRanges = RangesListFactory.create(dependency.getVersion());
      if (!requiredVersionRanges.isSatisfiedBy(new Semver(existVersion))) {
        var validationMessage = format("Application dependency by name %s and version %s not exist",
          dependency.getName(), dependency.getVersion());
        log.info("validateApplicationDependencies:: {}", validationMessage);
        throw new RequestValidationException(validationMessage);
      }
    }
  }

  private Set<InterfaceReference> getProvidedInterfaces(List<ApplicationDescriptor> applicationDescriptors) {
    return applicationDescriptors
      .stream()
      .map(this::getModuleDescriptors)
      .flatMap(Collection::stream)
      .map(ModuleDescriptor::getProvides)
      .flatMap(Collection::stream)
      .map(interfaceDescriptor -> InterfaceReference.of(interfaceDescriptor.getId(),
        interfaceDescriptor.getVersion()))
      .collect(toSet());
  }

  private Set<InterfaceReference> getRequiredInterfaces(ApplicationDescriptor applicationDescriptor) {
    var descriptors = getModuleDescriptors(applicationDescriptor);
    return descriptors
      .stream()
      .map(ModuleDescriptor::getRequires)
      .flatMap(Collection::stream)
      .collect(toCollection(LinkedHashSet::new));
  }

  private List<ModuleDescriptor> getModuleDescriptors(ApplicationDescriptor applicationDescriptor) {
    return union(applicationDescriptor.getModuleDescriptors(), applicationDescriptor.getUiModuleDescriptors());
  }

  private String interfaceReferencesAsString(Set<InterfaceReference> interfaceReferences) {
    return interfaceReferences
      .stream()
      .map(interfaceReference -> interfaceReference.getId() + " " + interfaceReference.getVersion())
      .collect(joining(";"));
  }
}
