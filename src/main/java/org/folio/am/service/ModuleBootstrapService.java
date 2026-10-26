package org.folio.am.service;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.folio.am.utils.ModuleIdUtils.getNameAndVersion;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.dto.EgressBootstrap;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.domain.model.EgressProviderRow;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.mapper.ModuleBootstrapMapper;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.semver4j.Semver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModuleBootstrapService {

  private static final Comparator<ModuleBootstrapDiscovery> BY_MODULE_ID =
    Comparator.comparing(ModuleBootstrapDiscovery::getModuleId);

  /**
   * Orders providers of the same module name from least to most preferred: a valid semantic version beats an invalid
   * one, a higher version beats a lower one, and for equal versions (build metadata is ignored) the lower module id
   * is preferred. It is a total order, so the choice does not depend on the order in which candidates are visited.
   */
  private static final Comparator<ModuleBootstrapDiscovery> BY_PREFERENCE = Comparator
    .comparing(ModuleBootstrapService::versionOf, Comparator.nullsFirst(Comparator.<Semver>naturalOrder()))
    .thenComparing(BY_MODULE_ID.reversed());

  private final ModuleBootstrapRepository repository;
  private final ModuleBootstrapMapper mapper;

  /**
   * Retrieves a module bootstrap data including information for the modules that provides interfaces listed in
   * required/optional section of the module descriptor. Endpoint information is only returned for interfaces which the
   * specified module actually uses.
   *
   * @param moduleId - the module identifier
   * @return Module bootstrap data
   */
  @Transactional(readOnly = true)
  public ModuleBootstrap getById(String moduleId) {
    var views = repository.findAllRequiredByModuleId(moduleId);
    var moduleView = findModuleView(moduleId, views);
    return new ModuleBootstrap()
      .module(mapper.convert(moduleView))
      .requiredModules(resolveRequiredModules(moduleView, views));
  }

  /**
   * Ingress bootstrap: the module's own routes only, with no dependency resolution.
   */
  @Transactional(readOnly = true)
  public ModuleBootstrap getIngressBootstrap(String moduleId) {
    var views = repository.findViewsById(moduleId);
    if (views.isEmpty()) {
      throw new EntityNotFoundException("Module not found by id: " + moduleId);
    }
    return new ModuleBootstrap()
      .module(mapper.convert(views.getFirst()))
      .requiredModules(List.of());
  }

  /**
   * Egress bootstrap: finds the modules that provide the interfaces the given module requires or optionally requires,
   * looking only at the applications in the supplied scope. The module itself is used to confirm scope membership,
   * but is not returned.
   *
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Normalize the scope: a null or blank application id is rejected (400); the rest is de-duplicated and
   *   sorted.</li>
   *   <li>The module must be a BACKEND module of at least one scope application, otherwise 404.</li>
   *   <li>Load provider rows: one row per (provider module, scope application that contains it, required interface
   *   it provides). Only providers with a discovery URL are returned, never the module itself, and the interface
   *   comes from the stored copy in {@code module_interface_reference.provided_interface}, so module descriptors
   *   are not read.</li>
   *   <li>Fold the rows into one discovery per provider module: the lowest scope application id, and its interfaces
   *   without duplicates, sorted by interface id.</li>
   *   <li>Keep one provider per module name (highest version) and sort the result by module id, see
   *   {@link #selectProviders(List)}.</li>
   * </ol>
   *
   * <p>Example: {@code consumer-1.0.0} requires interface {@code orders}. The scope is {@code [app-b-1.0.0,
   * app-a-1.0.0, app-b-1.0.0]}, which is normalized to {@code [app-a-1.0.0, app-b-1.0.0]}. Data:</p>
   * <pre>
   * mod-orders-1.0.0   in app-a and app-b   provides orders, orders-admin   discovery set
   * mod-orders-1.1.0   in app-b             provides orders                 discovery set
   * mod-users-2.0.0    in app-a             provides users                  discovery set (not required)
   * mod-audit-1.0.0    in app-c (no scope)  provides orders                 discovery set
   * mod-fees-1.0.0     in app-a             provides orders                 no discovery
   * </pre>
   *
   * <p>Step 3 returns these rows ({@code orders-admin}, {@code mod-users}, {@code mod-audit} and {@code mod-fees}
   * are filtered out by the query):</p>
   * <pre>
   * (mod-orders-1.0.0, app-a-1.0.0, orders)
   * (mod-orders-1.0.0, app-b-1.0.0, orders)
   * (mod-orders-1.1.0, app-b-1.0.0, orders)
   * </pre>
   *
   * <p>Step 4 gives {@code mod-orders-1.0.0} (application {@code app-a-1.0.0}, interfaces {@code [orders]}) and
   * {@code mod-orders-1.1.0} (application {@code app-b-1.0.0}, interfaces {@code [orders]}). Step 5 sees two versions
   * of {@code mod-orders} and keeps {@code mod-orders-1.1.0}, so the response is:</p>
   * <pre>
   * requiredModules: [ mod-orders-1.1.0 (application app-b-1.0.0, location, interfaces [orders]) ]
   * </pre>
   *
   * @param moduleId - the requesting module id
   * @param applicationIds - the application scope
   * @return providers of the required interfaces, see the algorithm above
   */
  @Transactional(readOnly = true)
  public EgressBootstrap getEgressBootstrap(String moduleId, List<String> applicationIds) {
    var scope = normalizeApplicationIds(applicationIds);
    if (!repository.existsByIdAndApplicationIdIn(moduleId, scope)) {
      throw new EntityNotFoundException("Module not found by id: " + moduleId);
    }

    var rows = repository.findEgressProviders(moduleId, scope);
    return new EgressBootstrap().requiredModules(selectProviders(toProviderDiscoveries(rows)));
  }

  /**
   * Folds provider rows into one discovery per module. A row is one (provider, application, interface), so a module
   * and its interfaces repeat once for every in-scope application that contains the provider.
   *
   * <p>Example: rows {@code (mod-a-1.0.0, app-2, x)}, {@code (mod-a-1.0.0, app-1, x)} and
   * {@code (mod-a-1.0.0, app-1, y)} become one discovery of {@code mod-a-1.0.0}, see
   * {@link #toProviderDiscovery(List)}.</p>
   */
  private List<ModuleBootstrapDiscovery> toProviderDiscoveries(List<EgressProviderRow> rows) {
    var rowsByModule = rows.stream().collect(groupingBy(EgressProviderRow::moduleId));
    return mapItems(rowsByModule.values(), this::toProviderDiscovery);
  }

  /**
   * Builds the discovery of one provider: the lowest in-scope application id, and its required interfaces without
   * duplicates, sorted by interface id.
   *
   * <p>Example: rows {@code (mod-a-1.0.0, app-2, x)}, {@code (mod-a-1.0.0, app-1, x)} and
   * {@code (mod-a-1.0.0, app-1, y)} give application {@code app-1} and interfaces {@code [x, y]}: the application id
   * is the smallest of {@code app-2} and {@code app-1}, and {@code x} appears once although two rows carry it.</p>
   */
  private ModuleBootstrapDiscovery toProviderDiscovery(List<EgressProviderRow> moduleRows) {
    var row = moduleRows.getFirst();
    var applicationId = moduleRows.stream().map(EgressProviderRow::applicationId).min(naturalOrder()).orElseThrow();

    var interfacesById = new TreeMap<String, InterfaceDescriptor>();
    moduleRows.forEach(r -> interfacesById.putIfAbsent(r.providedInterface().getId(), r.providedInterface()));

    return new ModuleBootstrapDiscovery()
      .moduleId(row.moduleId())
      .applicationId(applicationId)
      .location(row.location())
      .systemUserRequired(row.systemUserRequired())
      .interfaces(mapItems(interfacesById.values(), mapper::convert));
  }

  /**
   * Legacy ({@code GET /modules/{id}}) counterpart of the egress algorithm, working on the views that
   * {@code findAllRequiredByModuleId} returned: the module itself and, with no application scope, all providers of
   * its required interfaces.
   *
   * <p>Every row of the module itself is skipped (a module that belongs to several applications is returned once per
   * application), as are providers without a discovery URL. The remaining views are trimmed to the required
   * interfaces and passed to {@link #selectProviders(List)}.</p>
   */
  private List<ModuleBootstrapDiscovery> resolveRequiredModules(ModuleBootstrapView moduleView,
    List<ModuleBootstrapView> views) {
    var requiredInterfaces = getRequiredOptionalInterfaces(moduleView);
    if (requiredInterfaces.isEmpty()) {
      return List.of();
    }

    // the same module can be returned once per owning application, so every self row is skipped, not just one
    var candidates = views.stream()
      .filter(view -> !moduleView.getId().equals(view.getId()))
      .filter(view -> view.getLocation() != null)
      .map(view -> toRequiredModuleDiscovery(view, requiredInterfaces))
      .toList();

    return selectProviders(candidates);
  }

  /**
   * Converts the view first and filters the converted interfaces, so the loaded descriptor is never modified.
   */
  private ModuleBootstrapDiscovery toRequiredModuleDiscovery(ModuleBootstrapView view, Set<String> required) {
    var discovery = mapper.convert(view);
    discovery.setInterfaces(filterRequired(discovery.getInterfaces(), required));
    return discovery;
  }

  /**
   * Keeps one provider per module name: the highest semantic version, with ties broken by the lowest module id. The
   * result is sorted by module id, so the response does not depend on the order of the rows.
   *
   * <p>The name and version come from the module id: the version is the part after the last dash. Example: from
   * {@code mod-a-1.0.0}, {@code mod-a-2.0.0}, {@code mod-a-1.5.0} and {@code mod-b-1.0.0} the result is
   * {@code [mod-a-2.0.0, mod-b-1.0.0]}. {@code mod-a-1.0.0+1} and {@code mod-a-1.0.0+2} have equal versions (build
   * metadata is ignored), so {@code mod-a-1.0.0+1}, the lower module id, is kept.</p>
   *
   * <p>Only versions of the same module name compete. Different modules that provide the same interface, which is
   * the normal case for interfaces of type {@code multiple}, are all kept: {@code mod-a-1.0.0} and
   * {@code mod-b-3.0.0} both providing {@code dashboard} are both returned, and the caller chooses between them with
   * the module id header. Two versions of one module in the same scope are collapsed to the highest one, as in
   * {@code GET /modules/{id}}.</p>
   */
  private static List<ModuleBootstrapDiscovery> selectProviders(List<ModuleBootstrapDiscovery> candidates) {
    var preferredByName = new HashMap<String, ModuleBootstrapDiscovery>();
    for (var candidate : candidates) {
      var name = getNameAndVersion(candidate.getModuleId()).getLeft();
      preferredByName.merge(name, candidate, ModuleBootstrapService::preferred);
    }
    return preferredByName.values().stream().sorted(BY_MODULE_ID).toList();
  }

  private static ModuleBootstrapDiscovery preferred(ModuleBootstrapDiscovery current,
    ModuleBootstrapDiscovery candidate) {
    return BY_PREFERENCE.compare(candidate, current) > 0 ? candidate : current;
  }

  private static Semver versionOf(ModuleBootstrapDiscovery discovery) {
    return Semver.parse(getNameAndVersion(discovery.getModuleId()).getRight());
  }

  private static ModuleBootstrapView findModuleView(String moduleId, List<ModuleBootstrapView> views) {
    return views.stream()
      .filter(view -> moduleId.equals(view.getId()))
      .findFirst()
      .orElseThrow(() -> new EntityNotFoundException("Module not found by id: " + moduleId));
  }

  private static Set<String> getRequiredOptionalInterfaces(ModuleBootstrapView moduleView) {
    var descriptor = moduleView.getDescriptor();
    return Stream.concat(toStream(descriptor.getRequires()), toStream(descriptor.getOptional()))
      .map(InterfaceReference::getId)
      .collect(toSet());
  }

  private static List<ModuleBootstrapInterface> filterRequired(List<ModuleBootstrapInterface> interfaces,
    Set<String> requiredInterfaces) {
    return toStream(interfaces).filter(i -> requiredInterfaces.contains(i.getId())).toList();
  }

  private static List<String> normalizeApplicationIds(List<String> applicationIds) {
    if (applicationIds.stream().anyMatch(StringUtils::isBlank)) {
      throw new RequestValidationException("Application id must not be blank",
        "applicationIds", String.valueOf(applicationIds));
    }
    return applicationIds.stream().distinct().sorted().toList();
  }
}
