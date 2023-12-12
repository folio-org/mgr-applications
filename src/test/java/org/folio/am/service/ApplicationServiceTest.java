package org.folio.am.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.service.validator.ValidationMode.ON_CREATE;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.APPLICATION_NAME;
import static org.folio.am.support.TestConstants.APPLICATION_VERSION;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_NAME;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_NAME;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.utils.CollectionUtils.mapItemsToSet;
import static org.folio.test.TestConstants.TENANT_ID;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.entity.ArtifactEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.integration.mte.EntitlementService;
import org.folio.am.mapper.ApplicationDescriptorMapper;
import org.folio.am.repository.ApplicationRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.repository.UiModuleRepository;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  private static final String UI_MODULE_FOO_ID = "test-ui-module-foo-1.0.0";
  private static final String UI_MODULE_FOO_NAME = "test-ui-module-foo";
  private static final String UI_MODULE_BAR_ID = "test-ui-module-bar-1.0.0";
  private static final String UI_MODULE_BAR_NAME = "test-ui-module-bar";

  @InjectMocks private ApplicationService service;
  @Mock private ApplicationRepository repository;
  @Mock private ModuleRepository moduleRepository;
  @Mock private UiModuleRepository uiModuleRepository;
  @Mock private ApplicationDescriptorMapper mapper;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ModuleDiscoveryService discoveryService;
  @Mock private EntitlementService entitlementService;
  @Mock private ApplicationValidatorService applicationValidatorService;
  @Mock private ModuleDescriptorLoader moduleDescriptorLoader;

  @BeforeEach
  public void init() {
    service.setEntitlementService(entitlementService);
  }

  @Test
  void get_positive() {
    var expectedEntity = TestValues.applicationDescriptorEntity();
    when(repository.getReferenceById(APPLICATION_ID)).thenReturn(expectedEntity);
    var actual = service.get(APPLICATION_ID, true);
    assertThat(actual).isEqualTo(TestValues.applicationDescriptor());
  }

  @Test
  void get_negative_entityNotFound() {
    var errorMessage = "Unable to find ApplicationDescriptorEntity with id " + APPLICATION_ID;
    when(repository.getReferenceById(APPLICATION_ID)).thenThrow(new EntityNotFoundException(errorMessage));
    assertThatThrownBy(() -> service.get(APPLICATION_ID, true))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage(errorMessage);
  }

  @Test
  void fndByQuery_positive() {
    var entities = singletonList(TestValues.applicationDescriptorEntity());
    var cqlQuery = "cql.allRecords = 1";
    var expectedPage = new PageImpl<>(entities, Pageable.ofSize(1), 100);
    when(repository.findByCql(cqlQuery, OffsetRequest.of(0, 10))).thenReturn(expectedPage);

    var actual = service.findByQuery(cqlQuery, 0, 10, true);

    assertThat(actual).isEqualTo(SearchResult.of(100, singletonList(TestValues.applicationDescriptor())));
  }

  @Test
  void fndByQuery_positive_emptyQuery() {
    var entities = singletonList(TestValues.applicationDescriptorEntity());
    var cqlQuery = "";
    var expectedPage = new PageImpl<>(entities, Pageable.ofSize(1), 100);
    when(repository.findAll(OffsetRequest.of(0, 10))).thenReturn(expectedPage);

    var actual = service.findByQuery(cqlQuery, 0, 10, true);

    assertThat(actual).isEqualTo(SearchResult.of(100, singletonList(TestValues.applicationDescriptor())));
  }

  @Test
  void findByIds_positive() {
    var applicationIds = singletonList(APPLICATION_ID);
    when(repository.findByIds(applicationIds)).thenReturn(singletonList(TestValues.applicationDescriptorEntity()));
    var actual = service.findByIds(applicationIds, true);
    assertThat(actual).containsExactly(TestValues.applicationDescriptor());
  }

  @Test
  void create_positive() {
    var descriptor = TestValues.applicationDescriptor();
    var context = TestValues.validationContext(descriptor, List.of(ON_CREATE));
    var entity = TestValues.applicationDescriptorEntity();
    var moduleIds = mapItemsToSet(entity.getModules(), ArtifactEntity::getId);

    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    when(mapper.convert(descriptor)).thenReturn(entity);
    when(moduleRepository.findAllById(moduleIds)).thenReturn(emptyList());
    when(repository.save(entity)).thenReturn(entity);

    var actual = service.create(descriptor, OKAPI_AUTH_TOKEN, true);

    assertThat(actual).isEqualTo(descriptor);
    verify(eventPublisher).publishDescriptorCreate(descriptor, OKAPI_AUTH_TOKEN);
    verify(applicationValidatorService).validate(context);
    verify(moduleDescriptorLoader).loadByUrls(descriptor.getModules());
    verify(moduleDescriptorLoader).loadByUrls(descriptor.getUiModules());
  }

  @Test
  void create_positive_validationDisabled() {
    var descriptor = TestValues.applicationDescriptor();
    var entity = TestValues.applicationDescriptorEntity();

    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    when(mapper.convert(descriptor)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);

    var actual = service.create(descriptor, OKAPI_AUTH_TOKEN, false);

    assertThat(actual).isEqualTo(descriptor);
    verify(eventPublisher).publishDescriptorCreate(descriptor, OKAPI_AUTH_TOKEN);
    verifyNoInteractions(applicationValidatorService);
  }

  @Test
  void create_positive_idNotSet() {
    var descriptor = TestValues.applicationDescriptor(null);
    var context = TestValues.validationContext(descriptor, List.of(ON_CREATE));
    var expectedDescriptor = TestValues.applicationDescriptor();
    var expectedDescriptorEntity = TestValues.applicationDescriptorEntity();

    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    when(mapper.convert(expectedDescriptor)).thenReturn(expectedDescriptorEntity);
    when(repository.save(expectedDescriptorEntity)).thenReturn(expectedDescriptorEntity);

    var actual = service.create(descriptor, OKAPI_AUTH_TOKEN, true);

    assertThat(actual).isEqualTo(expectedDescriptor);
    verify(eventPublisher).publishDescriptorCreate(descriptor, OKAPI_AUTH_TOKEN);
    verify(applicationValidatorService).validate(context);
  }

  @Test
  void create_positive_discoveryCopied() {
    final var descriptor = TestValues.applicationDescriptor();
    final var context = TestValues.validationContext(descriptor, List.of(ON_CREATE));
    final var entity = TestValues.applicationDescriptorEntity();
    final var moduleIds = mapItemsToSet(entity.getModules(), ArtifactEntity::getId);
    final var module = first(entity.getModules());
    final var dbModule = copyOf(module);
    dbModule.setDiscoveryUrl("http://test.url");

    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    when(mapper.convert(descriptor)).thenReturn(entity);
    when(moduleRepository.findAllById(moduleIds)).thenReturn(List.of(dbModule));
    when(repository.save(entity)).thenReturn(entity);

    var actual = service.create(descriptor, OKAPI_AUTH_TOKEN, true);

    assertThat(actual).isEqualTo(descriptor);
    assertThat(entity.getModules())
      .anyMatch(me -> me.getDiscoveryUrl().equals(dbModule.getDiscoveryUrl()));

    verify(eventPublisher).publishDescriptorCreate(descriptor, OKAPI_AUTH_TOKEN);
    verify(applicationValidatorService).validate(context);
    verify(moduleDescriptorLoader).loadByUrls(descriptor.getModules());
    verify(moduleDescriptorLoader).loadByUrls(descriptor.getUiModules());
  }

  @Test
  void create_negative_entityExists() {
    var descriptor = TestValues.applicationDescriptor(null);

    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(TestValues.applicationDescriptorEntity()));

    assertThatThrownBy(() -> service.create(descriptor, OKAPI_AUTH_TOKEN, true))
      .isInstanceOf(EntityExistsException.class)
      .hasMessage("Application descriptor already created with id: " + APPLICATION_ID);
    verifyNoInteractions(eventPublisher);
    verifyNoInteractions(applicationValidatorService);
  }

  @Test
  void delete_positive() {
    var expectedEntityToDelete = TestValues.applicationDescriptorEntity(descriptorWithSeveralModules());
    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(expectedEntityToDelete));

    when(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN)).thenReturn(List.of());
    expectedEntityToDelete.getModules().forEach(module -> {
      when(repository.existsByNotIdAndModuleId(expectedEntityToDelete.getId(), module.getId())).thenReturn(false);
      doNothing().when(discoveryService).delete(module.getId(), OKAPI_AUTH_TOKEN);
      doNothing().when(moduleRepository).delete(module);
    });
    expectedEntityToDelete.getUiModules().forEach(uiModule -> {
      when(repository.existsByNotIdAndUiModuleId(expectedEntityToDelete.getId(), uiModule.getId()))
        .thenReturn(false);
      doNothing().when(uiModuleRepository).delete(uiModule);
    });

    service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    verify(repository).delete(expectedEntityToDelete);

    verify(eventPublisher).publishDescriptorDelete(expectedEntityToDelete.getApplicationDescriptor(), OKAPI_AUTH_TOKEN);
  }

  @Test
  void delete_positive_teIntegrationDisabled() {
    service.setEntitlementService(null);
    var expectedEntityToDelete = TestValues.applicationDescriptorEntity(descriptorWithSeveralModules());
    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(expectedEntityToDelete));

    expectedEntityToDelete.getModules().forEach(module -> {
      when(repository.existsByNotIdAndModuleId(expectedEntityToDelete.getId(), module.getId())).thenReturn(false);
      doNothing().when(discoveryService).delete(module.getId(), OKAPI_AUTH_TOKEN);
      doNothing().when(moduleRepository).delete(module);
    });
    expectedEntityToDelete.getUiModules().forEach(uiModule -> {
      when(repository.existsByNotIdAndUiModuleId(expectedEntityToDelete.getId(), uiModule.getId()))
        .thenReturn(false);
      doNothing().when(uiModuleRepository).delete(uiModule);
    });

    service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    verify(repository).delete(expectedEntityToDelete);

    verify(eventPublisher).publishDescriptorDelete(expectedEntityToDelete.getApplicationDescriptor(), OKAPI_AUTH_TOKEN);
    verifyNoInteractions(entitlementService);
  }

  @Test
  void delete_positive_modulesBelongToOtherApp() {
    var expectedEntityToDelete = TestValues.applicationDescriptorEntity(descriptorWithSeveralModules());
    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(expectedEntityToDelete));

    when(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN)).thenReturn(List.of());
    expectedEntityToDelete.getModules().forEach(module ->
      when(repository.existsByNotIdAndModuleId(expectedEntityToDelete.getId(), module.getId())).thenReturn(true));
    expectedEntityToDelete.getUiModules().forEach(uiModule -> {
      when(repository.existsByNotIdAndUiModuleId(expectedEntityToDelete.getId(), uiModule.getId()))
        .thenReturn(false);
      doNothing().when(uiModuleRepository).delete(uiModule);
    });

    service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    verify(repository).delete(expectedEntityToDelete);

    verify(eventPublisher).publishDescriptorDelete(expectedEntityToDelete.getApplicationDescriptor(), OKAPI_AUTH_TOKEN);
  }

  @Test
  void delete_positive_uiModulesBelongToOtherApp() {
    var expectedEntityToDelete = TestValues.applicationDescriptorEntity(descriptorWithSeveralModules());
    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(expectedEntityToDelete));

    when(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN)).thenReturn(List.of());
    expectedEntityToDelete.getModules().forEach(module -> {
      when(repository.existsByNotIdAndModuleId(expectedEntityToDelete.getId(), module.getId()))
        .thenReturn(false);
      doNothing().when(discoveryService).delete(module.getId(), OKAPI_AUTH_TOKEN);
      doNothing().when(moduleRepository).delete(module);
    });
    expectedEntityToDelete.getUiModules().forEach(uiModule ->
      when(repository.existsByNotIdAndUiModuleId(expectedEntityToDelete.getId(), uiModule.getId())).thenReturn(true));

    service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN);

    verify(repository).delete(expectedEntityToDelete);

    verify(eventPublisher).publishDescriptorDelete(expectedEntityToDelete.getApplicationDescriptor(), OKAPI_AUTH_TOKEN);
  }

  @Test
  void delete_negative_entityNotFound() {
    assertThatThrownBy(() -> service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Unable to find application descriptor with id " + APPLICATION_ID);
  }

  @Test
  void delete_negative_entityExistInTe() {
    var expectedEntityToDelete = TestValues.applicationDescriptorEntity();
    var tenants = List.of(TENANT_ID);
    when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(expectedEntityToDelete));

    when(entitlementService.getTenants(APPLICATION_ID, OKAPI_AUTH_TOKEN)).thenReturn(tenants);

    assertThatThrownBy(() -> service.delete(APPLICATION_ID, OKAPI_AUTH_TOKEN))
      .isInstanceOf(EntityExistsException.class)
      .hasMessage("Application Descriptor cannot be removed "
        + "because it is installed for tenants: " + tenants);
  }

  @Test
  void findApplicationsByModuleIds() {
    var moduleIds = List.of(SERVICE_ID);
    var applicationDescriptor = TestValues.applicationDescriptor();
    when(repository.findApplicationsByModuleIds(moduleIds)).thenReturn(
      List.of(TestValues.applicationDescriptorEntity(applicationDescriptor)));

    var actual = service.findApplicationsByModuleIds(moduleIds);

    assertThat(actual).isEqualTo(List.of(applicationDescriptor));
  }

  private static ApplicationDescriptor descriptorWithSeveralModules() {
    return TestValues.applicationDescriptor(APPLICATION_ID, APPLICATION_NAME, APPLICATION_VERSION)
      .addModulesItem(new Module().id(MODULE_FOO_ID).name(MODULE_FOO_NAME).version(SERVICE_VERSION))
      .addModulesItem(new Module().id(MODULE_BAR_ID).name(MODULE_BAR_NAME).version(SERVICE_VERSION))
      .addModuleDescriptorsItem(new ModuleDescriptor().id(MODULE_FOO_ID))
      .addModuleDescriptorsItem(new ModuleDescriptor().id(MODULE_BAR_ID))
      .addUiModulesItem(new Module().id(UI_MODULE_FOO_ID).name(UI_MODULE_FOO_NAME).version(SERVICE_VERSION))
      .addUiModulesItem(new Module().id(UI_MODULE_BAR_ID).name(UI_MODULE_BAR_NAME).version(SERVICE_VERSION))
      .addUiModuleDescriptorsItem(new ModuleDescriptor().id(UI_MODULE_FOO_ID))
      .addUiModuleDescriptorsItem(new ModuleDescriptor().id(UI_MODULE_BAR_ID));
  }

  private static ModuleEntity first(Set<ModuleEntity> modules) {
    return modules.stream().findFirst()
      .orElseThrow(() -> new IllegalArgumentException("At least one module is required"));
  }

  private static ModuleEntity copyOf(ModuleEntity module) {
    var result = ModuleEntity.of(module.getId());

    result.setName(module.getName());
    result.setVersion(module.getVersion());
    result.setDescriptor(module.getDescriptor());
    result.setInterfaces(module.getInterfaces());
    result.setDiscoveryUrl(module.getDiscoveryUrl());

    return result;
  }
}
