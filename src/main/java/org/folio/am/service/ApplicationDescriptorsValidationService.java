package org.folio.am.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.SemverUtils.getVersion;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.error.Parameter;
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
    var allResolvedAppDescriptorsByName = getApplicationDescriptorsByName(descriptors);

    log.info("Validate descriptors: ids = {}", () -> toAppIdsString(descriptors));

    toStream(descriptors)
      .flatMap(ad -> toStream(ad.getDependencies())).distinct()
      .forEach(dependency -> resolveDependencyToAppDescriptors(dependency, allResolvedAppDescriptorsByName));

    var allDescriptors = new ArrayList<>(allResolvedAppDescriptorsByName.values());
    log.info("Validate applications including dependencies: ids = {}", () -> toAppIdsString(allDescriptors));

    dependenciesValidator.validate(allDescriptors);

    return toStream(allDescriptors)
      .map(ApplicationDescriptor::getId)
      .sorted().toList();
  }

  /**
   * Resolve the dependency to an application descriptor and recursively resolve its dependencies.
   * If the dependency is already resolved, then validate the version range to ensure compatibility.
   *
   * @param dependency                the dependency to resolve
   * @param resolvedDescriptorsByName the map of already resolved application descriptors by name
   */
  private void resolveDependencyToAppDescriptors(Dependency dependency,
    Map<String, ApplicationDescriptor> resolvedDescriptorsByName) {
    if (resolvedDescriptorsByName.containsKey(dependency.getName())) {
      // already resolved, just validate the version range
      // to ensure the dependency is compatible with the already resolved application
      var resolved = resolvedDescriptorsByName.get(dependency.getName());

      validateRangeOnResolvedApp(dependency, resolved);

      log.debug("Application dependency already resolved: dependency = {}, application = {}",
        dependency, resolved.getId());
      return;
    }

    var resolvedDescriptor = getLatestApplicationMatchingDependency(dependency);
    log.info("Dependency resolved to application: dependency = {}, application = {}",
      dependency, resolvedDescriptor != null ? resolvedDescriptor.getId() : null);

    if (resolvedDescriptor != null) {
      resolvedDescriptorsByName.put(resolvedDescriptor.getName(), resolvedDescriptor);

      toStream(resolvedDescriptor.getDependencies())
        .forEach(dep -> resolveDependencyToAppDescriptors(dep, resolvedDescriptorsByName));
    }
  }

  /**
   * Find the latest application which satisfies the dependency.
   * If not found and the dependency is optional, then return null.
   * If not found and the dependency is required, then throw RequestValidationException.
   *
   * @param dependency the dependency to resolve
   * @return the latest application which satisfies the dependency or null if the dependency is optional and not found
   * @throws RequestValidationException if the dependency is required and not found
   */
  private @Nullable ApplicationDescriptor getLatestApplicationMatchingDependency(Dependency dependency) {
    var dependencyVersionRange = semverRangeFrom(dependency);

    return applicationService.findAllApplicationIdsByName(dependency.getName()).stream()
      .filter(appVersionIsInRange(dependencyVersionRange))
      .max(bySemver())
      .map(latestAppId -> applicationService.get(latestAppId, true))
      .orElseGet(() -> {
        if (dependency.getOptional()) {
          log.info("Cannot find optional dependency application which satisfies the dependency: "
            + "name = {}, version = {}", dependency.getName(), dependency.getVersion());
          return null;
        } else {
          throw new RequestValidationException("Cannot find application which satisfies the dependency",
            List.of(
              param("dependencyName", dependency.getName()),
              param("dependencyVersion", dependency.getVersion()))
          );
        }
      });
  }

  private void validateRangeOnResolvedApp(Dependency dependency, ApplicationDescriptor resolved) {
    var dependencyVersionRange = semverRangeFrom(dependency);

    if (!appVersionIsInRange(dependencyVersionRange).test(resolved.getId())) {
      throw new RequestValidationException(
        format("Dependency version range '%s' is not satisfied by already resolved application '%s' with version '%s'."
            + " Check that all dependencies for the '%s' app are compatible",
          dependency.getVersion(), resolved.getName(), resolved.getVersion(), resolved.getName()),
        List.of(
          param("dependencyName", dependency.getName()),
          param("dependencyVersion", dependency.getVersion()))
      );
    }
  }

  /**
   * Convert the list of application descriptors to a map by name.
   * If there are duplicate names, then throw RequestValidationException.
   *
   * @param descriptors the list of application descriptors
   * @return the map of application descriptors by name
   * @throws RequestValidationException if there are duplicate names
   */
  private static Map<String, ApplicationDescriptor> getApplicationDescriptorsByName(
    List<ApplicationDescriptor> descriptors) {
    var result = new HashMap<String, ApplicationDescriptor>();

    for (var descriptor : emptyIfNull(descriptors)) {
      if (result.containsKey(descriptor.getName())) {
        throw new RequestValidationException("Duplicate application descriptor with the same name in the request",
          "name", descriptor.getName());
      }

      result.put(descriptor.getName(), descriptor);
    }
    return result;
  }

  private static String toAppIdsString(Collection<ApplicationDescriptor> descriptors) {
    return toStream(descriptors).map(ApplicationDescriptor::getId).collect(joining(", "));
  }

  private static Comparator<String> bySemver() {
    return Comparator.comparing(appId -> new Semver(getVersion(appId)));
  }

  private static RangesList semverRangeFrom(Dependency dependency) {
    return RangesListFactory.create(dependency.getVersion(), true);
  }

  private static Parameter param(String key, String value) {
    return new Parameter().key(key).value(value);
  }

  private static Predicate<String> appVersionIsInRange(RangesList requiredVersionRanges) {
    return appId -> requiredVersionRanges.isSatisfiedBy(new Semver(getVersion(appId)));
  }
}
