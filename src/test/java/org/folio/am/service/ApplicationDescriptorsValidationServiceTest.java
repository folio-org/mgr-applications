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
import org.folio.am.mapper.ApplicationDescriptorToDtoMapper;
import org.folio.am.mapper.ApplicationEntityToDtoMapper;
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
  @Spy private ApplicationDescriptorToDtoMapper applicationDescriptorToDtoMapper =
    new org.folio.am.mapper.ApplicationDescriptorToDtoMapperImpl();
  @Spy private final ApplicationEntityToDtoMapper applicationEntityToDtoMapper =
    new org.folio.am.mapper.ApplicationEntityToDtoMapperImpl();
  @Spy private DependenciesValidator dependenciesValidator;
  @InjectMocks private ApplicationDescriptorsValidationService applicationDescriptorsValidationService;

  @Test
  void validate_positive() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var beModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("configuration").version("1.0.0 2.0.0")));
    applicationDescriptor1.setModuleDescriptors(List.of(beModuleDescriptor));
    var uiModuleDescriptor = new ModuleDescriptor()
      .requires(List.of(new InterfaceReference().id("ui-settings").version("1.0.0")));
    applicationDescriptor1.setUiModuleDescriptors(List.of(uiModuleDescriptor));
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.1");
    applicationDescriptor2.setId("app2-2.0.1");
    var beModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("configuration").version("1.0.0")));
    applicationDescriptor2.setModuleDescriptors(List.of(beModuleDescriptor2));
    var uiModuleDescriptor2 = new ModuleDescriptor()
      .provides(List.of(new InterfaceDescriptor().id("ui-settings").version("1.0.0")));
    applicationDescriptor2.setUiModuleDescriptors(List.of(uiModuleDescriptor2));

    var actual = applicationDescriptorsValidationService
      .validate(List.of(applicationDescriptor1, applicationDescriptor2));
    var expected = List.of("app1-1.0.0", "app2-2.0.1");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_negative_providedSameApplicationsWithDifferentVersions() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var dependency = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.2");
    applicationDescriptor2.setId("app2-2.0.2");

    var applicationDescriptor3 = new ApplicationDescriptor();
    applicationDescriptor3.setName("app2");
    applicationDescriptor3.setVersion("2.1.0");
    applicationDescriptor3.setId("app2-2.1.0");

    var descriptors = List.of(applicationDescriptor1, applicationDescriptor2, applicationDescriptor3);
    assertThatThrownBy(() -> applicationDescriptorsValidationService
      .validate(descriptors))
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
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setName("app2");
    applicationEntity1.setVersion("2.0.2");
    applicationEntity1.setId("app2-2.0.2");

    var applicationEntity2 = new ApplicationEntity();
    applicationEntity2.setName("app2");
    applicationEntity2.setVersion("2.0.3");
    applicationEntity2.setId("app2-2.0.3");
    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.3");
    applicationDescriptor2.setId("app2-2.0.3");
    var dependency2 = new Dependency().name("app3").version("3.0.0");
    applicationDescriptor2.setDependencies(List.of(dependency2));
    applicationEntity2.setApplicationDescriptor(applicationDescriptor2);

    var applicationEntity3 = new ApplicationEntity();
    applicationEntity3.setName("app3");
    applicationEntity3.setVersion("3.0.0");
    applicationEntity3.setId("app3-3.0.0");

    when(applicationService.findByName("app2")).thenReturn(List.of(applicationEntity1, applicationEntity2));
    when(applicationService.findByName("app3")).thenReturn(List.of(applicationEntity3));

    var actual = applicationDescriptorsValidationService.validate(List.of(applicationDescriptor1));
    var expected = List.of("app1-1.0.0", "app2-2.0.3", "app3-3.0.0");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_positive_byLatestRetrievedOrProvidedDependencyVersionIfProvidedIsLast() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.3");
    applicationDescriptor2.setId("app2-2.0.3");

    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setName("app2");
    applicationEntity1.setVersion("2.0.2");
    applicationEntity1.setId("app2-2.0.2");

    when(applicationService.findByName("app2")).thenReturn(List.of(applicationEntity1));

    var actual =
      applicationDescriptorsValidationService.validate(List.of(applicationDescriptor1, applicationDescriptor2));
    var expected = List.of("app1-1.0.0", "app2-2.0.3");

    assertThat(actual).isEqualTo(expected);
    verify(dependenciesValidator).validate(anyList());
  }

  @Test
  void validate_negative_byLatestRetrievedOrProvidedDependencyVersionIfRetrievedIsLast() {
    var applicationDescriptor1 = new ApplicationDescriptor();
    applicationDescriptor1.setName("app1");
    applicationDescriptor1.setVersion("1.0.0");
    applicationDescriptor1.setId("app1-1.0.0");
    var dependency1 = new Dependency().name("app2").version("^2.0.1");
    applicationDescriptor1.setDependencies(List.of(dependency1));

    var applicationDescriptor2 = new ApplicationDescriptor();
    applicationDescriptor2.setName("app2");
    applicationDescriptor2.setVersion("2.0.2");
    applicationDescriptor2.setId("app2-2.0.2");

    var applicationEntity1 = new ApplicationEntity();
    applicationEntity1.setName("app2");
    applicationEntity1.setVersion("2.0.3");
    applicationEntity1.setId("app2-2.0.3");

    when(applicationService.findByName("app2")).thenReturn(List.of(applicationEntity1));

    var descriptors = List.of(applicationDescriptor1, applicationDescriptor2);

    assertThatThrownBy(() -> applicationDescriptorsValidationService
      .validate(descriptors))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Used same applications with different versions")
      .satisfies(error -> {
        var params = ((RequestValidationException) error).getErrorParameters();
        assertThat(params).isEqualTo(List.of(new Parameter().key("applicationNames").value("app2")));
      });
    verify(dependenciesValidator).validate(anyList());
  }
}
