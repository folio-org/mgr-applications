package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ApplicationEntityMapper;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDescriptorsValidationServiceTest {

  @Mock private ApplicationService applicationService;
  @Spy private final ApplicationEntityMapper applicationEntityMapper =
    new org.folio.am.mapper.ApplicationEntityMapperImpl();
  @Spy private DependenciesValidator dependenciesValidator;
  @InjectMocks private ApplicationDescriptorsValidationService applicationDescriptorsValidationService;

  @Test
  void validate_positive() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0 2.0")));
    applicationDescriptor1.setModuleDescriptors(List.of(beModuleDescriptor));
    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0")));
    applicationDescriptor1.setUiModuleDescriptors(List.of(uiModuleDescriptor));
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = getApplicationDescriptor("app2", "2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("configuration").version("1.0")));
    applicationDescriptor2.setModuleDescriptors(List.of(beModuleDescriptor2));
    var uiModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("ui-settings").version("1.0")));
    applicationDescriptor2.setUiModuleDescriptors(List.of(uiModuleDescriptor2));

    var actual = applicationDescriptorsValidationService
      .validateDescriptors(List.of(applicationDescriptor1, applicationDescriptor2));
    var expected = List.of("app1-1.0.0", "app2-2.0.1");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_positive_providedApplicationDescriptorsWithSameId() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var applicationDescriptor2 = getApplicationDescriptor("app1", "1.0.0");

    var actual = applicationDescriptorsValidationService
      .validateDescriptors(List.of(applicationDescriptor1, applicationDescriptor2));
    var expected = List.of("app1-1.0.0");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_negative_providedSameApplicationsWithDifferentVersions() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = getApplicationDescriptor("app2", "2.0.2");
    var applicationDescriptor3 = getApplicationDescriptor("app2", "2.1.0");

    var descriptors = List.of(applicationDescriptor1, applicationDescriptor2, applicationDescriptor3);
    assertThatThrownBy(() -> applicationDescriptorsValidationService
      .validateDescriptors(descriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Used same applications with different versions")
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("applicationNames").value("app2")));
      });
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_positive_byLatestRetrievedDependencyVersion() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationEntity1 = getApplicationEntity("app2", "2.0.2");
    var applicationEntity2 = getApplicationEntity("app2", "2.0.3");

    var applicationDescriptor2 = getApplicationDescriptor("app2", "2.0.3");
    var dependency2 = new Dependency().name("app3").version("3.0.0");
    applicationDescriptor2.setDependencies(List.of(dependency2));
    applicationEntity2.setApplicationDescriptor(applicationDescriptor2);

    var applicationEntity3 = getApplicationEntity("app3", "3.0.0");

    when(applicationService.findByNameWithModules("app2")).thenReturn(List.of(applicationEntity1, applicationEntity2));
    when(applicationService.findByNameWithModules("app3")).thenReturn(List.of(applicationEntity3));

    var actual = applicationDescriptorsValidationService.validateDescriptors(List.of(applicationDescriptor1));
    var expected = List.of("app1-1.0.0", "app2-2.0.3", "app3-3.0.0");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_positive_byLatestRetrievedOrProvidedDependencyVersionIfProvidedIsLast() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationDescriptor2 = getApplicationDescriptor("app2", "2.0.3");
    var applicationEntity1 = getApplicationEntity("app2", "2.0.2");

    when(applicationService.findByNameWithModules("app2")).thenReturn(List.of(applicationEntity1));

    var actual = applicationDescriptorsValidationService
      .validateDescriptors(List.of(applicationDescriptor1, applicationDescriptor2));
    var expected = List.of("app1-1.0.0", "app2-2.0.3");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_negative_byLatestRetrievedOrProvidedDependencyVersionIfRetrievedIsLast() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationDescriptor2 = getApplicationDescriptor("app2", "2.0.2");
    var applicationEntity1 = getApplicationEntity("app2", "2.0.3");

    when(applicationService.findByNameWithModules("app2")).thenReturn(List.of(applicationEntity1));

    var descriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    assertThatThrownBy(() -> applicationDescriptorsValidationService
      .validateDescriptors(descriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Used same applications with different versions")
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("applicationNames").value("app2")));
      });
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_positive_includePrereleaseInDependencyCheckRange() {
    var applicationDescriptor1 = getApplicationDescriptor("app1", "1.0.0");
    var dependency = new Dependency().name("app2").version("^1.2.0-SNAPSHOT");
    applicationDescriptor1.setDependencies(List.of(dependency));
    // use prerelease version
    var applicationEntity1 = getApplicationEntity("app2", "1.3.0-SNAPSHOT.100000000000001");
    var applicationEntity2 = getApplicationEntity("app2", "1.3.0-SNAPSHOT.100000000000002");
    var applicationDescriptor2 = getApplicationDescriptor("app2", "1.3.0-SNAPSHOT.100000000000002");

    when(applicationService.findByNameWithModules("app2")).thenReturn(List.of(applicationEntity1,
      applicationEntity2));

    var actual = applicationDescriptorsValidationService
      .validateDescriptors(List.of(applicationDescriptor1));
    var expected = List.of("app1-1.0.0", "app2-1.3.0-SNAPSHOT.100000000000002");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(List.of(applicationDescriptor1, applicationDescriptor2));
  }

  private ApplicationDescriptor getApplicationDescriptor(String name, String version) {
    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setName(name);
    applicationDescriptor.setVersion(version);
    applicationDescriptor.setId(name + "-" + version);
    return applicationDescriptor;
  }

  private ApplicationEntity getApplicationEntity(String name, String version) {
    var applicationEntity = new ApplicationEntity();
    applicationEntity.setName(name);
    applicationEntity.setVersion(version);
    applicationEntity.setId(name + "-" + version);
    return applicationEntity;
  }
}
