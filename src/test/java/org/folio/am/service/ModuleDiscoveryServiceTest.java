package org.folio.am.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.MODULE_URL;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.UI_MODULE_ID;
import static org.folio.am.support.TestConstants.UI_MODULE_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ModuleDiscoveryRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleDiscoveryServiceTest {

  @Mock private ModuleRepository repository;
  @Mock private ModuleDiscoveryMapper mapper;
  @Mock private ModuleDiscoveryRepository moduleDiscoveryRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Captor private ArgumentCaptor<ModuleDiscovery> moduleDiscoveryCaptor;

  @InjectMocks private ModuleDiscoveryService service;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository, mapper, eventPublisher);
  }

  @Nested
  @DisplayName("get")
  class Get {

    @Test
    void positive() {
      when(repository.findByHasDiscoveryAndId(MODULE_ID)).thenReturn(Optional.of(TestValues.moduleEntity()));
      when(mapper.convert(TestValues.moduleEntity())).thenReturn(TestValues.moduleDiscovery());

      var actual = service.get(MODULE_ID);

      assertThat(actual).isEqualTo(TestValues.moduleDiscovery());
    }

    @Test
    void negative_discoveryNotFound() {
      when(repository.findByHasDiscoveryAndId(MODULE_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.get(MODULE_ID))
        .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void positive_uiModule() {
      var uiModule = TestValues.uiModuleEntity(UI_MODULE_URL);
      when(repository.findByHasDiscoveryAndId(UI_MODULE_ID)).thenReturn(Optional.of(uiModule));
      when(mapper.convert(uiModule)).thenReturn(TestValues.uiModuleDiscovery());

      var actual = service.get(UI_MODULE_ID);

      assertThat(actual).isEqualTo(TestValues.uiModuleDiscovery());
    }

    @Test
    void negative_uiModuleDiscoveryNotFound() {
      when(repository.findByHasDiscoveryAndId(UI_MODULE_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.get(UI_MODULE_ID))
        .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("search")
  class Search {

    private static final int OFFSET = 0;
    private static final int LIMIT = 25;

    @Test
    void positive_queryIsNotNull() {
      var query = "cql.allRecords = 1";
      var entity = TestValues.moduleDiscoveryEntity();
      var offsetRequest = OffsetRequest.of(OFFSET, LIMIT);
      when(moduleDiscoveryRepository.findByCql(query, offsetRequest)).thenReturn(new PageImpl<>(List.of(entity)));
      when(mapper.convert(List.of(entity))).thenReturn(List.of(TestValues.moduleDiscovery()));

      var actual = service.search(query, LIMIT, OFFSET);

      assertThat(actual).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
    }

    @Test
    void positive_queryIsNull() {
      var entity = TestValues.moduleDiscoveryEntity();
      var expectedOffsetRequest = OffsetRequest.of(OFFSET, LIMIT);
      when(moduleDiscoveryRepository.findAll(expectedOffsetRequest)).thenReturn(new PageImpl<>(List.of(entity)));
      when(mapper.convert(List.of(entity))).thenReturn(List.of(TestValues.moduleDiscovery()));

      var actual = service.search(null, LIMIT, OFFSET);

      assertThat(actual).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
    }
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    void positive() {
      var moduleEntity = TestValues.moduleEntity();
      var expectedModuleDiscovery = TestValues.moduleDiscovery();

      when(repository.findById(MODULE_ID)).thenReturn(Optional.of(moduleEntity));
      when(repository.saveAndFlush(any())).thenReturn(moduleEntity);
      doNothing().when(eventPublisher).publishDiscoveryCreate(expectedModuleDiscovery, ModuleType.BACKEND,
        OKAPI_AUTH_TOKEN);
      when(mapper.convert(moduleEntity)).thenReturn(expectedModuleDiscovery);

      var moduleDiscovery = TestValues.moduleDiscovery().id(null);
      var result = service.create(MODULE_ID, moduleDiscovery, OKAPI_AUTH_TOKEN);

      assertThat(result).isEqualTo(expectedModuleDiscovery);
      assertThat(moduleDiscovery.getId()).isEqualTo(MODULE_ID);
    }

    @Test
    void negative_moduleIsNotFoundById() {
      when(repository.findById(MODULE_ID)).thenReturn(Optional.empty());

      var moduleDiscovery = TestValues.moduleDiscovery().id(null);
      assertThatThrownBy(() -> service.create(MODULE_ID, moduleDiscovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Unable to find module with id: %s", MODULE_ID);
    }

    @Test
    void negative_moduleAlreadyHasDiscoveryUrl() {
      var moduleEntity = TestValues.moduleEntity(MODULE_URL);
      when(repository.findById(MODULE_ID)).thenReturn(Optional.of(moduleEntity));

      var moduleDiscovery = TestValues.moduleDiscovery().id(null);
      assertThatThrownBy(() -> service.create(MODULE_ID, moduleDiscovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityExistsException.class)
        .hasMessage("Discovery information already present for module: %s", MODULE_ID);
    }

    @Test
    void positive_batchRequest() {
      var moduleEntity = TestValues.moduleEntity();
      var expectedModuleDiscovery = TestValues.moduleDiscovery();

      when(repository.findAllById(List.of(MODULE_ID))).thenReturn(List.of(moduleEntity));
      when(repository.saveAndFlush(any())).thenReturn(moduleEntity);
      doNothing().when(eventPublisher).publishDiscoveryCreate(expectedModuleDiscovery, ModuleType.BACKEND,
        OKAPI_AUTH_TOKEN);
      when(mapper.convert(moduleEntity)).thenReturn(TestValues.moduleDiscovery());

      var moduleDiscovery = TestValues.moduleDiscovery().id(null);
      var moduleDiscoveries = new ModuleDiscoveries().discovery(List.of(moduleDiscovery));
      var result = service.create(moduleDiscoveries, OKAPI_AUTH_TOKEN);

      assertThat(result).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
    }

    @Test
    void positive_invalidIdInBatchRequest() {
      var moduleDiscovery = TestValues.moduleDiscovery().id("invalid id");
      var moduleDiscoveries = TestValues.moduleDiscoveries(moduleDiscovery);

      assertThatThrownBy(() -> service.create(moduleDiscoveries, OKAPI_AUTH_TOKEN))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("Discovery id must match name-version pattern")
        .satisfies(exception ->
          assertThat(((RequestValidationException) exception).getErrorParameters())
            .containsExactly(new Parameter().key("id").value("[" + MODULE_ID + "]")));
    }

    @Test
    void positive_batchRequestAndModuleIsNotFoundById() {
      var moduleDiscovery = TestValues.moduleDiscovery();
      var moduleDiscoveries = TestValues.moduleDiscoveries(moduleDiscovery);
      when(repository.findAllById(List.of(MODULE_ID))).thenReturn(emptyList());

      assertThatThrownBy(() -> service.create(moduleDiscoveries, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Modules are not found for ids: %s", List.of(MODULE_ID));
    }

    @Test
    void positive_batchRequestAndModuleDiscoveryUrlIsPresent() {
      var moduleDiscovery = TestValues.moduleDiscovery();
      var moduleDiscoveries = TestValues.moduleDiscoveries(moduleDiscovery);
      var moduleEntity = TestValues.moduleEntity(MODULE_URL);
      when(repository.findAllById(List.of(MODULE_ID))).thenReturn(List.of(moduleEntity));

      assertThatThrownBy(() -> service.create(moduleDiscoveries, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityExistsException.class)
        .hasMessage("Module Discovery already exists for ids: %s", List.of(MODULE_ID));
    }

    @Test
    void positive_uiModule() {
      var uiModuleEntity = TestValues.uiModuleEntity();
      var expectedUiModuleDiscovery = TestValues.uiModuleDiscovery();

      when(repository.findById(UI_MODULE_ID)).thenReturn(Optional.of(uiModuleEntity));
      when(repository.saveAndFlush(any())).thenReturn(uiModuleEntity);
      doNothing().when(eventPublisher).publishDiscoveryCreate(expectedUiModuleDiscovery, ModuleType.UI,
        OKAPI_AUTH_TOKEN);
      when(mapper.convert(uiModuleEntity)).thenReturn(expectedUiModuleDiscovery);

      var uiModuleDiscovery = TestValues.uiModuleDiscovery().id(null);
      var result = service.create(UI_MODULE_ID, uiModuleDiscovery, OKAPI_AUTH_TOKEN);

      assertThat(result).isEqualTo(expectedUiModuleDiscovery);
      assertThat(uiModuleDiscovery.getId()).isEqualTo(UI_MODULE_ID);
    }

    @Test
    void negative_uiModuleNotFoundById() {
      when(repository.findById(UI_MODULE_ID)).thenReturn(Optional.empty());

      var uiModuleDiscovery = TestValues.uiModuleDiscovery().id(null);
      assertThatThrownBy(() -> service.create(UI_MODULE_ID, uiModuleDiscovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Unable to find module with id: %s", UI_MODULE_ID);
    }

    @Test
    void negative_uiModuleAlreadyHasDiscoveryUrl() {
      var uiModuleEntity = TestValues.uiModuleEntity(UI_MODULE_URL);
      when(repository.findById(UI_MODULE_ID)).thenReturn(Optional.of(uiModuleEntity));

      var uiModuleDiscovery = TestValues.uiModuleDiscovery().id(null);
      assertThatThrownBy(() -> service.create(UI_MODULE_ID, uiModuleDiscovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityExistsException.class)
        .hasMessage("Discovery information already present for module: %s", UI_MODULE_ID);
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    void positive() {
      var moduleEntity = TestValues.moduleEntity();
      var discovery = TestValues.moduleDiscovery();

      when(repository.findById(MODULE_ID)).thenReturn(Optional.of(moduleEntity));
      when(repository.saveAndFlush(any())).thenReturn(moduleEntity);
      when(mapper.convert(moduleEntity)).thenReturn(discovery);
      doNothing().when(eventPublisher).publishDiscoveryUpdate(moduleDiscoveryCaptor.capture(), eq(ModuleType.BACKEND),
        eq(OKAPI_AUTH_TOKEN));

      service.update(MODULE_ID, discovery, OKAPI_AUTH_TOKEN);

      verify(eventPublisher).publishDiscoveryUpdate(any(ModuleDiscovery.class), eq(ModuleType.BACKEND),
        eq(OKAPI_AUTH_TOKEN));

      var capturedValue = moduleDiscoveryCaptor.getValue();
      assertThat(capturedValue).usingRecursiveComparison().ignoringFields("instId").isEqualTo(discovery);
    }

    @Test
    void negative_moduleIdDoesntMatch() {
      ModuleDiscovery discovery = TestValues.moduleDiscovery();
      discovery.setId("invalid");

      assertThatThrownBy(() -> service.update(MODULE_ID, discovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void negative_moduleIdNotEqualToArtifactId() {
      var discovery = TestValues.moduleDiscovery();
      discovery.setName("mod-some");
      discovery.setVersion("0.0.0");

      assertThatThrownBy(() -> service.update(MODULE_ID, discovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void negative_moduleNotFound() {
      var discovery = TestValues.moduleDiscovery();
      when(repository.findById(MODULE_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.update(MODULE_ID, discovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void positive_uiModule() {
      var uiModuleEntity = TestValues.uiModuleEntity(UI_MODULE_URL);
      var uiDiscovery = TestValues.uiModuleDiscovery();

      when(repository.findById(UI_MODULE_ID)).thenReturn(Optional.of(uiModuleEntity));
      when(repository.saveAndFlush(any())).thenReturn(uiModuleEntity);
      when(mapper.convert(uiModuleEntity)).thenReturn(uiDiscovery);
      doNothing().when(eventPublisher).publishDiscoveryUpdate(moduleDiscoveryCaptor.capture(), eq(ModuleType.UI),
        eq(OKAPI_AUTH_TOKEN));

      service.update(UI_MODULE_ID, uiDiscovery, OKAPI_AUTH_TOKEN);

      verify(eventPublisher).publishDiscoveryUpdate(any(ModuleDiscovery.class), eq(ModuleType.UI),
        eq(OKAPI_AUTH_TOKEN));

      var capturedValue = moduleDiscoveryCaptor.getValue();
      assertThat(capturedValue).usingRecursiveComparison().ignoringFields("instId").isEqualTo(uiDiscovery);
    }

    @Test
    void negative_uiModuleNotFound() {
      var uiDiscovery = TestValues.uiModuleDiscovery();
      when(repository.findById(UI_MODULE_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.update(UI_MODULE_ID, uiDiscovery, OKAPI_AUTH_TOKEN))
        .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    void positive() {
      var entity = TestValues.moduleEntity();
      when(repository.findByHasDiscoveryAndId(MODULE_ID)).thenReturn(Optional.of(entity));
      when(repository.save(entity)).thenReturn(entity);

      doNothing().when(eventPublisher).publishDiscoveryDelete(MODULE_ID, MODULE_ID, ModuleType.BACKEND,
        OKAPI_AUTH_TOKEN);

      service.delete(MODULE_ID, OKAPI_AUTH_TOKEN);

      verify(eventPublisher).publishDiscoveryDelete(SERVICE_ID, SERVICE_ID, ModuleType.BACKEND, OKAPI_AUTH_TOKEN);
    }

    @Test
    void positive_moduleNotFound() {
      when(repository.findByHasDiscoveryAndId(MODULE_ID)).thenReturn(Optional.empty());

      service.delete(MODULE_ID, OKAPI_AUTH_TOKEN);

      verify(eventPublisher, times(0)).publishDiscoveryDelete(SERVICE_ID, SERVICE_ID, ModuleType.BACKEND,
        OKAPI_AUTH_TOKEN);
    }

    @Test
    void positive_uiModule() {
      var uiEntity = TestValues.uiModuleEntity(UI_MODULE_URL);
      when(repository.findByHasDiscoveryAndId(UI_MODULE_ID)).thenReturn(Optional.of(uiEntity));
      when(repository.save(uiEntity)).thenReturn(uiEntity);

      doNothing().when(eventPublisher).publishDiscoveryDelete(UI_MODULE_ID, UI_MODULE_ID, ModuleType.UI,
        OKAPI_AUTH_TOKEN);

      service.delete(UI_MODULE_ID, OKAPI_AUTH_TOKEN);

      verify(eventPublisher).publishDiscoveryDelete(UI_MODULE_ID, UI_MODULE_ID, ModuleType.UI, OKAPI_AUTH_TOKEN);
    }

    @Test
    void positive_uiModuleNotFound() {
      when(repository.findByHasDiscoveryAndId(UI_MODULE_ID)).thenReturn(Optional.empty());

      service.delete(UI_MODULE_ID, OKAPI_AUTH_TOKEN);

      verify(eventPublisher, times(0)).publishDiscoveryDelete(UI_MODULE_ID, UI_MODULE_ID, ModuleType.UI,
        OKAPI_AUTH_TOKEN);
    }
  }
}
