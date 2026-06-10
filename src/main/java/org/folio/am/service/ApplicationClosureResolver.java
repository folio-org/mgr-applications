package org.folio.am.service;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationClosureResolver {

  private final ApplicationRepository applicationRepository;

  /**
   * Computes the transitive closure of application dependencies starting from the given IDs.
   *
   * @param applicationIds root application IDs
   * @return set of all application IDs reachable via the dependency graph (including roots)
   */
  public Set<String> resolve(Set<String> applicationIds) {
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<>(applicationIds);
    while (!queue.isEmpty()) {
      var batch = drainUnvisited(queue, visited);
      if (batch.isEmpty()) {
        continue;
      }
      applicationRepository.findAllById(batch).stream()
        .flatMap(entity -> extractDependencyIds(entity).stream())
        .filter(depId -> !visited.contains(depId))
        .forEach(queue::add);
    }
    return visited;
  }

  private Set<String> drainUnvisited(ArrayDeque<String> queue, Set<String> visited) {
    var batch = new HashSet<String>();
    while (!queue.isEmpty()) {
      var id = queue.poll();
      if (visited.add(id)) {
        batch.add(id);
      }
    }
    return batch;
  }

  private Set<String> extractDependencyIds(ApplicationEntity entity) {
    var descriptor = entity.getApplicationDescriptor();
    if (descriptor == null) {
      log.debug("Application {} has null descriptor or no dependencies; closure narrows here", entity.getId());
      return Set.of();
    }
    var deps = descriptor.getDependencies();
    if (CollectionUtils.isEmpty(deps)) {
      log.debug("Application {} has null descriptor or no dependencies; closure narrows here", entity.getId());
      return Set.of();
    }
    var result = new HashSet<String>();
    for (var dep : deps) {
      result.add(dep.getName() + "-" + dep.getVersion());
    }
    return result;
  }
}
