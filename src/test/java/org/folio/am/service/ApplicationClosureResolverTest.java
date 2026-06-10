package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.repository.ApplicationRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationClosureResolverTest {

  @Mock
  ApplicationRepository applicationRepository;

  @InjectMocks
  ApplicationClosureResolver resolver;

  @Test
  void resolve_returnsTransitiveClosureOfDependencies() {
    var rootDescriptor = descriptor(new Dependency("app-platform-complete", "1.2.0"));
    var depDescriptor = descriptor(new Dependency("app-platform-base", "0.5.0"));
    var baseDescriptor = new ApplicationDescriptor();

    when(applicationRepository.findAllById(Set.of("app-platform-minimal-2.0.53")))
      .thenReturn(List.of(entity("app-platform-minimal-2.0.53", rootDescriptor)));
    when(applicationRepository.findAllById(Set.of("app-platform-complete-1.2.0")))
      .thenReturn(List.of(entity("app-platform-complete-1.2.0", depDescriptor)));
    when(applicationRepository.findAllById(Set.of("app-platform-base-0.5.0")))
      .thenReturn(List.of(entity("app-platform-base-0.5.0", baseDescriptor)));

    var closure = resolver.resolve(Set.of("app-platform-minimal-2.0.53"));

    assertThat(closure).containsExactlyInAnyOrder(
      "app-platform-minimal-2.0.53",
      "app-platform-complete-1.2.0",
      "app-platform-base-0.5.0");
  }

  @Test
  void resolve_handlesCyclesWithoutInfiniteLoop() {
    // A-1 depends on B-1, B-1 depends on A-1 — mutual cycle
    var descriptorA = descriptor(new Dependency("B", "1"));
    var descriptorB = descriptor(new Dependency("A", "1"));

    when(applicationRepository.findAllById(Set.of("A-1")))
      .thenReturn(List.of(entity("A-1", descriptorA)));
    when(applicationRepository.findAllById(Set.of("B-1")))
      .thenReturn(List.of(entity("B-1", descriptorB)));

    assertThat(resolver.resolve(Set.of("A-1"))).containsExactlyInAnyOrder("A-1", "B-1");
  }

  private static ApplicationDescriptor descriptor(Dependency... deps) {
    var d = new ApplicationDescriptor();
    for (var dep : deps) {
      d.addDependenciesItem(dep);
    }
    return d;
  }

  private static ApplicationEntity entity(String id, ApplicationDescriptor descriptor) {
    var e = new ApplicationEntity();
    e.setId(id);
    e.setApplicationDescriptor(descriptor);
    return e;
  }
}
