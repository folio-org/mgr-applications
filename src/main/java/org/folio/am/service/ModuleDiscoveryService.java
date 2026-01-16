package org.folio.am.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.am.utils.CollectionUtils.filterAndMap;
import static org.folio.common.utils.CollectionUtils.mapItems;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ArtifactEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ModuleDiscoveryRepository;
import org.folio.am.repository.ModuleRepository;
import org.folio.common.domain.model.Artifact;
import org.folio.common.domain.model.OffsetRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ModuleDiscoveryService {

  private final ModuleRepository repository;
  private final ModuleDiscoveryRepository moduleDiscoveryRepository;
  private final ModuleDiscoveryMapper mapper;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Retrieves module discovery information by module id.
   *
   * @param moduleId - module identifier as {@link String}
   * @return {@link ModuleDiscovery} information by module id
   */
  @Transactional(readOnly = true)
  public ModuleDiscovery get(String moduleId) {
    log.info("Getting module discovery: moduleId = {}", moduleId);

    return mapper.convert(findModuleWithDiscovery(moduleId));
  }

  /**
   * Retrieves module discovery information using provided CQL query and pagination parameters.
   *
   * @param query - CQL query as {@link String} object
   * @param limit - a number of results in response
   * @param offset - offset in pagination from first record.
   * @return {@link ModuleDiscoveries} object with found module discovery descriptors
   */
  @Transactional(readOnly = true)
  public ModuleDiscoveries search(String query, int limit, int offset) {
    var pageable = OffsetRequest.of(offset, limit);
    var moduleEntitiesPage = isNotBlank(query)
      ? moduleDiscoveryRepository.findByCql(query, pageable)
      : moduleDiscoveryRepository.findAll(pageable);

    return new ModuleDiscoveries()
      .discovery(mapper.convert(moduleEntitiesPage.getContent()))
      .totalRecords(moduleEntitiesPage.getTotalElements());
  }

  /**
   * Creates a module discovery information record for the given module id and module discovery descriptor.
   *
   * @param moduleId - module identifier as {@link String}
   * @param moduleDiscovery - {@link ModuleDiscovery} information
   * @param token - x-okapi-token value
   * @return created {@link ModuleDiscovery} information
   */
  public ModuleDiscovery create(String moduleId, ModuleDiscovery moduleDiscovery, String token) {
    var moduleEntity = findModule(moduleId);
    if (moduleEntity.getDiscoveryUrl() != null) {
      throw new EntityExistsException("Discovery information already present for module: " + moduleId);
    }

    populateModuleDiscoveryId(moduleDiscovery);
    validateModuleDiscovery(moduleId, moduleDiscovery);

    return addDiscoveryUrlForModule(moduleEntity, moduleDiscovery.getLocation(), token);
  }

  /**
   * Creates a module discovery information records for the {@link ModuleDiscoveries} batch request.
   *
   * @param moduleDiscoveries - {@link ModuleDiscoveries} information batch request
   * @param token - x-okapi-token value
   * @return created {@link ModuleDiscovery} information
   */
  public ModuleDiscoveries create(ModuleDiscoveries moduleDiscoveries, String token) {
    var moduleDiscoveryMap = moduleDiscoveries.getDiscovery().stream()
      .collect(toMap(Artifact::getArtifactId, ModuleDiscovery::getLocation));

    var createdModuleEntities = getValidatedModuleEntities(moduleDiscoveries).stream()
      .map(entity -> addDiscoveryUrlForModule(entity, moduleDiscoveryMap.get(entity.getId()), token))
      .collect(toList());

    return new ModuleDiscoveries()
      .discovery(createdModuleEntities)
      .totalRecords((long) createdModuleEntities.size());
  }

  /**
   * Updates a module discovery information record for the given module id and module discovery descriptor.
   *
   * @param moduleId - module identifier as {@link String}
   * @param moduleDiscovery - {@link ModuleDiscovery} information
   * @param token - x-okapi-token value
   */
  public void update(String moduleId, ModuleDiscovery moduleDiscovery, String token) {
    validateModuleDiscovery(moduleId, moduleDiscovery);
    populateModuleDiscoveryId(moduleDiscovery);

    var location = moduleDiscovery.getLocation();
    log.info("Updating module discovery: moduleId = {}, discovery = {}", moduleId, location);
    var moduleEntity = findModule(moduleId);
    moduleEntity.setDiscoveryUrl(moduleDiscovery.getLocation());
    var updatedEntity = repository.saveAndFlush(moduleEntity);

    var newModuleDiscovery = mapper.convert(updatedEntity);
    eventPublisher.publishDiscoveryUpdate(newModuleDiscovery, moduleEntity.getType(), token);

    log.info("Module discovery updated: moduleId = {}", moduleId);
  }

  /**
   * Deletes module discovery information record for the given module id.
   *
   * @param moduleId - module identifier as {@link String}
   * @param token - x-okapi-token value
   */
  public void delete(String moduleId, String token) {
    log.info("Removing module discovery: moduleId = {}", moduleId);

    repository.findByHasDiscoveryAndId(moduleId)
      .ifPresent(module -> cleanModuleDiscoveryUrl(moduleId, token, module));
  }

  private static void validateModuleDiscovery(String moduleId, ModuleDiscovery moduleDiscovery) {
    if (!Objects.equals(moduleId, moduleDiscovery.getArtifactId())) {
      throw new RequestValidationException("Module id in the discovery should be equal to: " + moduleId,
        "id", moduleDiscovery.getId());
    }

    if (!Objects.equals(moduleDiscovery.getId(), moduleDiscovery.getArtifactId())) {
      throw new RequestValidationException("Module id must be based on the name and version",
        "id", moduleDiscovery.getId());
    }
  }

  private List<ModuleEntity> getValidatedModuleEntities(ModuleDiscoveries discoveries) {
    var discoveryDescriptors = discoveries.getDiscovery();

    var invalidIds = filterAndMap(discoveryDescriptors, notEqualIdAndArtifactId(), Artifact::getArtifactId);
    if (isNotEmpty(invalidIds)) {
      throw new RequestValidationException("Discovery id must match name-version pattern", "id", invalidIds.toString());
    }

    var moduleIds = mapItems(discoveryDescriptors, Artifact::getArtifactId);
    var moduleEntities = repository.findAllById(moduleIds);

    if (moduleEntities.size() != discoveryDescriptors.size()) {
      var foundModuleIds = mapItems(moduleEntities, ModuleEntity::getId);
      var notFoundModuleIds = ListUtils.subtract(moduleIds, foundModuleIds);
      throw new EntityNotFoundException("Modules are not found for ids: " + notFoundModuleIds);
    }

    var moduleIdsWithDiscoveryUrl = filterAndMap(moduleEntities, notNullDiscovery(), ArtifactEntity::getId);
    if (isNotEmpty(moduleIdsWithDiscoveryUrl)) {
      throw new EntityExistsException("Module Discovery already exists for ids: " + moduleIdsWithDiscoveryUrl);
    }

    var moduleEntityMap = moduleEntities.stream().collect(toMap(ArtifactEntity::getId, identity()));
    return mapItems(moduleIds, moduleEntityMap::get);
  }

  private ModuleDiscovery addDiscoveryUrlForModule(ModuleEntity entity, String location, String token) {
    var moduleId = entity.getId();
    log.info("Creating module discovery: moduleId = {}, discovery = {}", moduleId, location);
    entity.setDiscoveryUrl(location);
    var savedModule = repository.saveAndFlush(entity);
    var moduleDiscovery = mapper.convert(savedModule);

    eventPublisher.publishDiscoveryCreate(moduleDiscovery, entity.getType(), token);
    log.info("Module discovery created: moduleId = {}", moduleId);

    return moduleDiscovery;
  }

  private void cleanModuleDiscoveryUrl(String moduleId, String token, ModuleEntity module) {
    module.setDiscoveryUrl(null);
    repository.save(module);
    eventPublisher.publishDiscoveryDelete(module.getId(), module.getId(), module.getType(), token);
    log.info("Discovery deleted: moduleId = {}", moduleId);
  }

  private static void populateModuleDiscoveryId(ModuleDiscovery moduleDiscovery) {
    if (moduleDiscovery.getId() == null) {
      moduleDiscovery.setId(moduleDiscovery.getArtifactId());
    }
  }

  private ModuleEntity findModule(String moduleId) {
    return repository.findById(moduleId).orElseThrow(
      () -> new EntityNotFoundException("Unable to find module with id: " + moduleId));
  }

  private ModuleEntity findModuleWithDiscovery(String moduleId) {
    return repository.findByHasDiscoveryAndId(moduleId)
      .orElseThrow(() -> new EntityNotFoundException("Unable to find discovery of the module with id: " + moduleId));
  }

  private static Predicate<ModuleDiscovery> notEqualIdAndArtifactId() {
    return md -> isNotBlank(md.getId()) && !StringUtils.equals(md.getArtifactId(), md.getId());
  }

  private static Predicate<ModuleEntity> notNullDiscovery() {
    return moduleEntity -> moduleEntity.getDiscoveryUrl() != null;
  }
}
