package org.folio.am.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.am.utils.CollectionUtils.union;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationInterfaceValidatorService {

  private final ApplicationService applicationService;

  public void validate(ApplicationReferences applicationReferences) {
    var applicationEntities = applicationService
      .findByIdsWithModules(new ArrayList<>(applicationReferences.getApplicationIds()));
    validateApplications(applicationEntities);
    validateInterfaces(applicationEntities);
  }

  private void validateApplications(List<ApplicationEntity> applicationEntities) {
    log.info("validateApplications:: validate {}", () -> applicationEntities
      .stream()
      .map(ApplicationEntity::getId)
      .collect(joining(",")));
    var appNamesWithSeveralVersions = applicationEntities
      .stream()
      .collect(groupingBy(ApplicationEntity::getName, mapping(ApplicationEntity::getVersion, toSet())))
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().size() > 1)
      .map(Entry::getKey)
      .collect(joining(";"));
    if (isNotEmpty(appNamesWithSeveralVersions)) {
      var parameter = new Parameter().key("applicationNames").value(appNamesWithSeveralVersions);
      var errorMessage = "Provided same applications with different versions";
      log.info("validateApplications:: {}", errorMessage + " " + appNamesWithSeveralVersions);
      throw new RequestValidationException(errorMessage, List.of(parameter));
    }
    var mapApplicationNameToVersions = applicationEntities
      .stream()
      .collect(toMap(ApplicationEntity::getName, ApplicationEntity::getVersion));
    for (var applicationEntity : applicationEntities) {
      var applicationDescriptor = applicationEntity.getApplicationDescriptor();
      if (applicationDescriptor != null) {
        var dependencies = applicationDescriptor.getDependencies();
        validateApplicationDependencies(dependencies, mapApplicationNameToVersions);
      }
    }
  }

  private void validateApplicationDependencies(List<Dependency> dependencies,
    Map<String, String> mapApplicationNameToVersions) {
    for (var dependency : dependencies) {
      if (!mapApplicationNameToVersions.containsKey(dependency.getName())) {
        var errorMessage = format("Application dependency by name %s not exist", dependency.getName());
        log.info("validateApplicationDependencies:: {}", errorMessage);
        throw new RequestValidationException(errorMessage);
      }
      var existVersion = mapApplicationNameToVersions.get(dependency.getName());
      var requiredVersionRanges = RangesListFactory.create(dependency.getVersion());
      if (!requiredVersionRanges.isSatisfiedBy(new Semver(existVersion))) {
        var errorMessage = format("Application dependency by name %s and version %s not exist",
          dependency.getName(), dependency.getVersion());
        log.info("validateApplicationDependencies:: {}", errorMessage);
        throw new RequestValidationException(errorMessage);
      }
    }
  }

  private void validateInterfaces(List<ApplicationEntity> applicationEntities) {
    log.info("validateApplications:: validate {}", () -> applicationEntities
      .stream()
      .map(ApplicationEntity::getId)
      .collect(joining(",")));
    var provided = getProvidedInterfaces(applicationEntities);
    var missedInterfacesPerApplication = applicationEntities
      .stream()
      .collect(toMap(ApplicationEntity::getId, applicationEntity -> {
        var neededInterfaces = getRequiredInterfaces(applicationEntity);
        neededInterfaces.removeAll(provided);
        return interfaceReferencesAsString(neededInterfaces);
      }));
    var errorParameters = new ArrayList<Parameter>();
    for (var appId : missedInterfacesPerApplication.keySet()) {
      if (isNotEmpty(missedInterfacesPerApplication.get(appId))) {
        errorParameters.add(new Parameter().key(appId).value(missedInterfacesPerApplication.get(appId)));
      }
    }
    if (!errorParameters.isEmpty()) {
      throw new RequestValidationException("Missing dependencies found for the applications", errorParameters);
    }
  }

  private Set<InterfaceReference> getProvidedInterfaces(List<ApplicationEntity> applicationEntities) {
    return applicationEntities
      .stream()
      .map(this::getModuleDescriptorsOfApplication)
      .flatMap(Collection::stream)
      .map(ModuleDescriptor::getProvides)
      .flatMap(Collection::stream)
      .map(interfaceDescriptor -> InterfaceReference.of(interfaceDescriptor.getId(),
        interfaceDescriptor.getVersion()))
      .collect(toSet());
  }

  private Set<InterfaceReference> getRequiredInterfaces(ApplicationEntity applicationEntity) {
    var descriptors = getModuleDescriptorsOfApplication(applicationEntity);
    return descriptors
      .stream()
      .map(ModuleDescriptor::getRequires)
      .flatMap(Collection::stream)
      .collect(toCollection(LinkedHashSet::new));
  }

  private List<ModuleDescriptor> getModuleDescriptorsOfApplication(ApplicationEntity applicationEntity) {
    var beModuleDescriptors = applicationEntity.getModules()
      .stream()
      .map(ModuleEntity::getDescriptor)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
    var uiModuleDescriptors = applicationEntity.getUiModules()
      .stream()
      .map(UiModuleEntity::getDescriptor)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
    return union(beModuleDescriptors, uiModuleDescriptors);
  }

  private String interfaceReferencesAsString(Set<InterfaceReference> interfaceReferences) {
    return interfaceReferences
      .stream()
      .map(interfaceReference -> interfaceReference.getId() + " " + interfaceReference.getVersion())
      .collect(joining(";"));
  }
}
