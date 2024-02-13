package org.folio.am.service;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.am.service.validator.ValidationMode.ON_CREATE;
import static org.folio.am.utils.CollectionUtils.toStream;
import static org.folio.common.utils.CollectionUtils.mapItems;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDescriptors;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.integration.mte.EntitlementService;
import org.folio.am.mapper.ApplicationDescriptorMapper;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ApplicationRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.repository.UiModuleRepository;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationService {
  private final ModuleDiscoveryMapper moduleDiscoveryMapper;

  private final ApplicationRepository appRepository;
  private final ModuleRepository moduleRepository;
  private final UiModuleRepository uiModuleRepository;
  private final ApplicationDescriptorMapper mapper;
  private final ApplicationEventPublisher eventPublisher;
  private final ModuleDiscoveryService discoveryService;
  @Lazy private final ApplicationValidatorService applicationValidatorService;
  private final ModuleDescriptorLoader moduleDescriptorLoader;

  @Setter(onMethod_ = @Autowired(required = false))
  private EntitlementService entitlementService;

  /**
   * Retrieves application descriptor by id.
   *
   * @param id - application descriptor id.
   * @param includeModuleDescriptors - if true, module descriptors will be included in the response.
   * @return {@link ApplicationDescriptor} object
   * @throws EntityNotFoundException if records is not found by id.
   */
  @Transactional(readOnly = true)
  public ApplicationDescriptor get(String id, boolean includeModuleDescriptors) {
    var entity = appRepository.getReferenceById(id);

    return descriptorWithModules(includeModuleDescriptors).apply(entity);
  }

  /**
   * Returns list of application descriptors by their ids.
   *
   * @param ids - application descriptor ids
   * @return {@link List} with {@link ApplicationDescriptor} objects
   */
  @Transactional(readOnly = true)
  public List<ApplicationDescriptor> findByIds(List<String> ids, boolean includeModuleDescriptors) {
    return appRepository.findByIds(ids).stream()
      .map(descriptorWithModules(includeModuleDescriptors))
      .collect(toList());
  }

  /**
   * Retrieves application descriptors by CQL query.
   *
   * @param query - CQL query with search and filter conditions.
   * @param offset - offset which is used to paginate search results
   * @param limit - number of result to return
   * @param includeModuleDescriptors - if true, module descriptors will be included in the response.
   * @return {@link ApplicationDescriptors}
   */
  @Transactional(readOnly = true)
  public SearchResult<ApplicationDescriptor> findByQuery(String query, int offset, int limit,
    boolean includeModuleDescriptors) {
    var offsetReq = OffsetRequest.of(offset, limit);

    var page = isBlank(query) ? appRepository.findAll(offsetReq) : appRepository.findByCql(query, offsetReq);

    var applicationDescriptors = page
      .map(descriptorWithModules(includeModuleDescriptors))
      .getContent();

    return SearchResult.of((int) page.getTotalElements(), applicationDescriptors);
  }

  /**
   * Saves application descriptor to the database and register Module Descriptors in Okapi.
   *
   * @param descriptor - application descriptor object to save.
   * @param token - okapi token.
   * @return saved {@link ApplicationDescriptor} object
   */
  public ApplicationDescriptor create(ApplicationDescriptor descriptor, String token, boolean check) {
    log.debug("Creating Application Descriptor: {}", descriptor);

    fillIdForArtifacts(descriptor);
    var id = descriptor.getId();
    var descriptorById = appRepository.findById(id).map(descriptorWithModules(false));
    if (descriptorById.isPresent()) {
      throw new EntityExistsException("Application descriptor already created with id: " + id);
    }

    var moduleDescriptors = moduleDescriptorLoader.loadByUrls(descriptor.getModules());
    var uiModuleDescriptors = moduleDescriptorLoader.loadByUrls(descriptor.getUiModules());

    if (check) {
      var validationContext = buildValidationContext(descriptor, moduleDescriptors, uiModuleDescriptors);
      applicationValidatorService.validate(validationContext);
    }

    addModuleDescriptors(descriptor::setModuleDescriptors, descriptor::getModuleDescriptors, moduleDescriptors);
    addModuleDescriptors(descriptor::setUiModuleDescriptors, descriptor::getUiModuleDescriptors, uiModuleDescriptors);
    return createApplication(descriptor, token);
  }

  private static ValidationContext buildValidationContext(ApplicationDescriptor descriptor,
    List<ModuleDescriptor> moduleDescriptors, List<ModuleDescriptor> uiModuleDescriptors) {
    return ValidationContext.builder()
      .applicationDescriptor(descriptor)
      .loadedModuleDescriptors(moduleDescriptors)
      .loadedUiModuleDescriptors(uiModuleDescriptors)
      .additionalModes(List.of(ON_CREATE))
      .build();
  }

  /**
   * Deletes application descriptor by id.
   *
   * @param id - application descriptor id
   * @param token - okapi token.
   * @throws EntityNotFoundException if application descriptor is not found by id.
   */
  public void delete(String id, String token) {
    var application = appRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Unable to find application descriptor with id " + id));

    validateAppInstallations(id, token);

    var applicationDescriptor = getAppDescriptorWithModDescriptors(application);

    removeModulesFromApplication(application, token);
    appRepository.delete(application);

    eventPublisher.publishDescriptorDelete(applicationDescriptor, token);
    log.debug("Application Descriptor entity deleted: id = {}", application.getId());
  }

  /**
   * Provides application descriptors by module ids.
   *
   * @param moduleIds - list with module ids
   * @return list with found {@link ApplicationDescriptor} object
   */
  public List<ApplicationDescriptor> findApplicationsByModuleIds(List<String> moduleIds) {
    return appRepository.findApplicationsByModuleIds(moduleIds).stream()
      .map(ApplicationEntity::getApplicationDescriptor)
      .collect(toList());
  }

  private Function<ApplicationEntity, ApplicationDescriptor> descriptorWithModules(
    boolean includeModuleDescriptors) {
    return entity -> includeModuleDescriptors
      ? getAppDescriptorWithModDescriptors(entity)
      : entity.getApplicationDescriptor();
  }

  private ApplicationDescriptor getAppDescriptorWithModDescriptors(ApplicationEntity entity) {
    return entity.getApplicationDescriptor()
      .moduleDescriptors(mapItems(entity.getModules(), ModuleEntity::getDescriptor))
      .uiModuleDescriptors(mapItems(entity.getUiModules(), UiModuleEntity::getDescriptor));
  }

  private ApplicationDescriptor createApplication(ApplicationDescriptor descriptor, String token) {
    var entity = mapper.convert(descriptor);
    populateDiscoveryFromDb(entity.getModules());

    var saved = appRepository.save(entity);

    var md = descriptor.getModuleDescriptors();
    eventPublisher.publishDescriptorCreate(descriptor.moduleDescriptors(md), token);
    log.debug("Application Descriptor entity saved: id = {}", saved.getId());

    return saved.getApplicationDescriptor();
  }

  private void populateDiscoveryFromDb(Set<ModuleEntity> modules) {
    if (isEmpty(modules)) {
      return;
    }

    var modulesById = modules.stream().collect(toMap(ModuleEntity::getId, identity()));
    var dbModules = moduleRepository.findAllById(modulesById.keySet());

    dbModules.forEach(dbModule -> {
      var m = modulesById.get(dbModule.getId());
      m.setDiscoveryUrl(dbModule.getDiscoveryUrl());
    });
  }

  private void validateAppInstallations(String id, String token) {
    if (entitlementService != null) {
      var tenants = entitlementService.getTenants(id, token);
      if (isNotEmpty(tenants)) {
        throw new EntityExistsException("Application Descriptor cannot be removed "
          + "because it is installed for tenants: " + tenants);
      }
    }
  }

  private void removeModulesFromApplication(ApplicationEntity application, String token) {
    application.removeAllModules(onModuleRemovedFromApplication(application, token));
    application.removeAllUiModules(onUiModuleRemovedFromApplication(application));
  }

  private Consumer<ModuleEntity> onModuleRemovedFromApplication(ApplicationEntity application, String token) {
    return module -> {
      if (!isAnotherAppRelatedToModule(application, module)) {
        discoveryService.delete(module.getId(), token);
        moduleRepository.delete(module);
        log.debug("Module removed: id = {}", module.getId());
      } else {
        log.debug("Module is included in other application(s) and cannot be delete: id = {}", module.getId());
      }
    };
  }

  private Consumer<UiModuleEntity> onUiModuleRemovedFromApplication(ApplicationEntity application) {
    return uiModule -> {
      if (!isAnotherAppRelatedToUiModule(application, uiModule)) {
        uiModuleRepository.delete(uiModule);
        log.debug("UI module removed: id = {}", uiModule.getId());
      } else {
        log.debug("UI module is included in other application(s) and cannot be delete: id = {}", uiModule.getId());
      }
    };
  }

  private boolean isAnotherAppRelatedToModule(ApplicationEntity application, ModuleEntity module) {
    return appRepository.existsByNotIdAndModuleId(application.getId(), module.getId());
  }

  private boolean isAnotherAppRelatedToUiModule(ApplicationEntity application, UiModuleEntity uiModule) {
    return appRepository.existsByNotIdAndUiModuleId(application.getId(), uiModule.getId());
  }

  private static void fillIdForArtifacts(ApplicationDescriptor descriptor) {
    descriptor.setId(descriptor.getArtifactId());
    toStream(descriptor.getModules())
      .forEach(module -> module.setId(module.getArtifactId()));
    toStream(descriptor.getUiModules())
      .forEach(module -> module.setId(module.getArtifactId()));
  }

  /**
   * Safely adds module descriptors to the application descriptor.
   *
   * @param setModuleDescriptors - setter for module descriptors
   * @param getModuleDescriptors - getter for module descriptors
   * @param moduleDescriptors - module descriptors to add
   */
  private static void addModuleDescriptors(Consumer<List<ModuleDescriptor>> setModuleDescriptors,
    Supplier<List<ModuleDescriptor>> getModuleDescriptors, List<ModuleDescriptor> moduleDescriptors) {
    if (isNull(getModuleDescriptors.get())) {
      setModuleDescriptors.accept(moduleDescriptors);
    } else {
      getModuleDescriptors.get().addAll(moduleDescriptors);
    }
  }
}
