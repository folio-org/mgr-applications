package org.folio.am.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestValues.applicationDiscovery;
import static org.folio.am.support.TestValues.emptyApplicationDiscoveries;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.entity.ApplicationDiscoveryView;
import org.folio.am.domain.entity.ApplicationModuleDiscoveryProjection;
import org.folio.am.domain.entity.ModuleDiscoveryEntity;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ApplicationDiscoveryRepository;
import org.folio.am.repository.ModuleDiscoveryRepository;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.utils.SemverUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDiscoveryServiceTest {

  @InjectMocks private ApplicationDiscoveryService service;
  @Mock private ModuleDiscoveryMapper mapper;
  @Mock private ApplicationDiscoveryRepository applicationDiscoveryRepository;
  @Mock private ModuleDiscoveryRepository moduleDiscoveryRepository;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(mapper, applicationDiscoveryRepository, moduleDiscoveryRepository);
  }

  @Test
  void get_positive() {
    var entities = singletonList(TestValues.moduleDiscoveryEntity());
    var expectedPage = new PageImpl<>(entities);
    when(moduleDiscoveryRepository.findAllByApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);

    when(mapper.convert(List.of(TestValues.moduleDiscoveryEntity()))).thenReturn(List.of(TestValues.moduleDiscovery()));

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
  }

  @Test
  void get_positive_emptyResult() {
    var expectedPage = new PageImpl<ModuleDiscoveryEntity>(emptyList());
    when(moduleDiscoveryRepository.findAllByApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);
    when(mapper.convert(emptyList())).thenReturn(emptyList());

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.emptyModuleDiscoveries());
  }

  @Test
  void search_positive() {
    var query = "name==test-app*";
    var app = applicationDiscoveryView(APPLICATION_ID);
    var appPage = new PageImpl<>(List.of(app), OffsetRequest.of(0, 10), 1);
    var discoveryEntity = applicationModuleDiscoveryProjection(APPLICATION_ID, MODULE_ID);
    var discoveryDto = TestValues.moduleDiscovery();

    when(applicationDiscoveryRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(moduleDiscoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of(APPLICATION_ID)))
      .thenReturn(List.of(discoveryEntity));
    when(mapper.convert(discoveryEntity)).thenReturn(discoveryDto);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(1);
    assertThat(actual.getApplicationDiscoveries()).containsExactly(applicationDiscovery(APPLICATION_ID, discoveryDto));
    assertThat(actual.getTotalRecords()).isEqualTo(1);
  }

  @Test
  void search_positive_emptyQuery() {
    var app = applicationDiscoveryView(APPLICATION_ID);
    var appPage = new PageImpl<>(List.of(app), OffsetRequest.of(0, 10), 1);
    var discoveryEntity = applicationModuleDiscoveryProjection(APPLICATION_ID, MODULE_ID);
    var discoveryDto = TestValues.moduleDiscovery();

    when(applicationDiscoveryRepository.findAll(OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(moduleDiscoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of(APPLICATION_ID)))
      .thenReturn(List.of(discoveryEntity));
    when(mapper.convert(discoveryEntity)).thenReturn(discoveryDto);

    var actual = service.search(null, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(1);
    assertThat(actual.getApplicationDiscoveries()).containsExactly(applicationDiscovery(APPLICATION_ID, discoveryDto));
    assertThat(actual.getTotalRecords()).isEqualTo(1);
  }

  @Test
  void search_positive_emptyResult() {
    var query = "name==non-existent";
    var emptyPage = new PageImpl<ApplicationDiscoveryView>(emptyList());

    when(applicationDiscoveryRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(emptyPage);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isEqualTo(emptyApplicationDiscoveries());
  }

  @Test
  void search_positive_multipleAppsWithDiscovery() {
    var query = "name==test-app*";
    var app1 = applicationDiscoveryView("test-app-1.0.0");
    var app2 = applicationDiscoveryView("test-app-2.0.0");
    var appPage = new PageImpl<>(List.of(app1, app2), OffsetRequest.of(0, 10), 2);
    var discovery1 = applicationModuleDiscoveryProjection("test-app-1.0.0", "mod-1-1.0.0");
    var discovery2 = applicationModuleDiscoveryProjection("test-app-2.0.0", "mod-2-1.0.0");
    var discoveryDto1 = TestValues.moduleDiscovery("mod-1-1.0.0");
    var discoveryDto2 = TestValues.moduleDiscovery("mod-2-1.0.0");

    when(applicationDiscoveryRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(moduleDiscoveryRepository.findAllWithApplicationIdByApplicationIdsIn(
      List.of("test-app-1.0.0", "test-app-2.0.0"))).thenReturn(List.of(discovery1, discovery2));
    when(mapper.convert(discovery1)).thenReturn(discoveryDto1);
    when(mapper.convert(discovery2)).thenReturn(discoveryDto2);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(2);
    assertThat(actual.getApplicationDiscoveries()).containsExactlyInAnyOrder(
      applicationDiscovery("test-app-1.0.0", discoveryDto1),
      applicationDiscovery("test-app-2.0.0", discoveryDto2)
    );
    assertThat(actual.getTotalRecords()).isEqualTo(2);
  }

  @Test
  void search_positive_orderedResult() {
    var query = "name==test-app*";
    var app1 = applicationDiscoveryView("test-app-1.0.0");
    var app2 = applicationDiscoveryView("test-app-2.0.0");
    var appPage = new PageImpl<>(List.of(app1, app2), OffsetRequest.of(0, 10), 2);
    var discovery1 = applicationModuleDiscoveryProjection("test-app-1.0.0", "mod-1-1.0.0");
    var discovery2 = applicationModuleDiscoveryProjection("test-app-2.0.0", "mod-2-1.0.0");
    var discoveryDto1 = TestValues.moduleDiscovery("mod-1-1.0.0");
    var discoveryDto2 = TestValues.moduleDiscovery("mod-2-1.0.0");

    when(applicationDiscoveryRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    // Note: the order of discoveries returned by the repository is different from the order of applications in the page
    // but the service should return discoveries in the same order as applications in the page
    when(moduleDiscoveryRepository.findAllWithApplicationIdByApplicationIdsIn(
      List.of("test-app-1.0.0", "test-app-2.0.0"))).thenReturn(List.of(discovery2, discovery1));
    when(mapper.convert(discovery1)).thenReturn(discoveryDto1);
    when(mapper.convert(discovery2)).thenReturn(discoveryDto2);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(2);
    assertThat(actual.getApplicationDiscoveries()).containsExactly(
      applicationDiscovery("test-app-1.0.0", discoveryDto1),
      applicationDiscovery("test-app-2.0.0", discoveryDto2)
    );
    assertThat(actual.getTotalRecords()).isEqualTo(2);
  }

  private static ApplicationDiscoveryView applicationDiscoveryView(String id) {
    var entity = new ApplicationDiscoveryView();
    entity.setId(id);
    return entity;
  }

  private ApplicationModuleDiscoveryProjection applicationModuleDiscoveryProjection(String appId, String moduleId) {
    return new ApplicationModuleDiscoveryProjection() {
      @Override
      public String getApplicationId() {
        return appId;
      }

      @Override
      public String getId() {
        return moduleId;
      }

      @Override
      public String getName() {
        return SemverUtils.getName(moduleId);
      }

      @Override
      public String getVersion() {
        return SemverUtils.getVersion(moduleId);
      }

      @Override
      public String getLocation() {
        return "http://" + getName() + ":8080";
      }
    };
  }
}
