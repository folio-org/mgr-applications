package org.folio.am.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ApplicationModuleDiscoveryEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ApplicationRepository;
import org.folio.am.repository.ModuleDiscoveryRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.test.types.UnitTest;
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
  @Mock private ModuleRepository repository;
  @Mock private ModuleDiscoveryMapper mapper;
  @Mock private ApplicationRepository applicationRepository;
  @Mock private ModuleDiscoveryRepository discoveryRepository;

  @Test
  void get_positive() {
    var entities = singletonList(TestValues.moduleEntity());
    var expectedPage = new PageImpl<>(entities);
    when(repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);

    when(mapper.convert(TestValues.moduleEntity())).thenReturn(TestValues.moduleDiscovery());

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
  }

  @Test
  void get_positive_emptyResult() {
    var expectedPage = new PageImpl<ModuleEntity>(emptyList());
    when(repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.emptyModuleDiscoveries());
  }

  @Test
  void search_positive() {
    var query = "name==test-app*";
    var app = applicationEntity("test-app-1.0.0");
    var appPage = new PageImpl<>(List.of(app), OffsetRequest.of(0, 10), 1);
    var discoveryEntity = applicationModuleDiscoveryEntity("test-app-1.0.0", "mod-1-1.0.0");
    var discoveryDto = TestValues.moduleDiscovery();

    when(applicationRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(discoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of("test-app-1.0.0")))
      .thenReturn(List.of(discoveryEntity));
    when(mapper.convert(discoveryEntity)).thenReturn(discoveryDto);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(1);
    assertThat(actual.getApplicationDiscoveries().get(0).getApplicationId()).isEqualTo("test-app-1.0.0");
    assertThat(actual.getApplicationDiscoveries().get(0).getDiscovery()).containsExactly(discoveryDto);
    assertThat(actual.getTotalRecords()).isEqualTo(1L);
  }

  @Test
  void search() {
    var app = applicationEntity("test-app-1.0.0");
    var appPage = new PageImpl<>(List.of(app), OffsetRequest.of(0, 10), 1);
    var discoveryEntity = applicationModuleDiscoveryEntity("test-app-1.0.0", "mod-1-1.0.0");
    var discoveryDto = TestValues.moduleDiscovery();

    when(applicationRepository.findAll(OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(discoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of("test-app-1.0.0")))
      .thenReturn(List.of(discoveryEntity));
    when(mapper.convert(discoveryEntity)).thenReturn(discoveryDto);

    var actual = service.search(null, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(1);
    assertThat(actual.getApplicationDiscoveries().get(0).getApplicationId()).isEqualTo("test-app-1.0.0");
    assertThat(actual.getTotalRecords()).isEqualTo(1L);
  }

  @Test
  void search_positive_emptyResult() {
    var query = "name==non-existent";
    var emptyPage = new PageImpl<ApplicationEntity>(emptyList());

    when(applicationRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(emptyPage);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isEqualTo(TestValues.emptyApplicationDiscoveries());
  }

  @Test
  void search_positive_noDiscoveryModules() {
    var query = "name==test-app*";
    var app = applicationEntity("test-app-1.0.0");
    var appPage = new PageImpl<>(List.of(app), OffsetRequest.of(0, 10), 1);

    when(applicationRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(discoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of("test-app-1.0.0")))
      .thenReturn(emptyList());

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).isEmpty();
    assertThat(actual.getTotalRecords()).isEqualTo(1L);
  }

  @Test
  void search_positive_multipleAppsWithDiscovery() {
    var query = "name==test-app*";
    var app1 = applicationEntity("test-app-1.0.0");
    var app2 = applicationEntity("test-app-2.0.0");
    var appPage = new PageImpl<>(List.of(app1, app2), OffsetRequest.of(0, 10), 2);
    var discovery1 = applicationModuleDiscoveryEntity("test-app-1.0.0", "mod-1-1.0.0");
    var discovery2 = applicationModuleDiscoveryEntity("test-app-2.0.0", "mod-2-1.0.0");
    var discoveryDto1 = TestValues.moduleDiscovery("mod-1-1.0.0");
    var discoveryDto2 = TestValues.moduleDiscovery("mod-2-1.0.0");

    when(applicationRepository.findByCql(query, OffsetRequest.of(0, 10))).thenReturn(appPage);
    when(discoveryRepository.findAllWithApplicationIdByApplicationIdsIn(List.of("test-app-1.0.0", "test-app-2.0.0")))
      .thenReturn(List.of(discovery1, discovery2));
    when(mapper.convert(discovery1)).thenReturn(discoveryDto1);
    when(mapper.convert(discovery2)).thenReturn(discoveryDto2);

    var actual = service.search(query, 0, 10);

    assertThat(actual).isNotNull();
    assertThat(actual.getApplicationDiscoveries()).hasSize(2);
    assertThat(actual.getTotalRecords()).isEqualTo(2L);
  }

  private ApplicationEntity applicationEntity(String id) {
    var entity = new ApplicationEntity();
    entity.setId(id);
    return entity;
  }

  private ApplicationModuleDiscoveryEntity applicationModuleDiscoveryEntity(String appId, String moduleId) {
    return new ApplicationModuleDiscoveryEntity() {
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
        return "test-module";
      }

      @Override
      public String getVersion() {
        return "1.0.0";
      }

      @Override
      public String getLocation() {
        return "http://test-module:8080";
      }
    };
  }
}
