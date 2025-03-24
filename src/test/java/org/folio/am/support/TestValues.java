package org.folio.am.support;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestConstants.APPLICATION_NAME;
import static org.folio.am.support.TestConstants.APPLICATION_VERSION;
import static org.folio.am.support.TestConstants.MODULE_BAR_ID;
import static org.folio.am.support.TestConstants.MODULE_BAR_NAME;
import static org.folio.am.support.TestConstants.MODULE_BAR_URL;
import static org.folio.am.support.TestConstants.MODULE_BAR_VERSION;
import static org.folio.am.support.TestConstants.MODULE_FOO_ID;
import static org.folio.am.support.TestConstants.MODULE_FOO_NAME;
import static org.folio.am.support.TestConstants.MODULE_FOO_URL;
import static org.folio.am.support.TestConstants.MODULE_FOO_VERSION;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.MODULE_URL;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.support.TestConstants.id;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.DeploymentDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.domain.dto.ModuleBootstrapEndpoint;
import org.folio.am.domain.dto.ModuleBootstrapInterface;
import org.folio.am.domain.dto.ModuleDiscoveries;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ApplicationEntity;
import org.folio.am.domain.entity.InterfaceReferenceEntity;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.am.domain.entity.ModuleDiscoveryEntity;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.domain.entity.UiModuleEntity;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.service.validator.ValidationMode;
import org.folio.common.domain.model.AnyDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestValues {

  public static ApplicationDescriptor applicationDescriptor() {
    return applicationDescriptor(APPLICATION_ID);
  }

  public static ApplicationReferences applicationReferences() {
    return new ApplicationReferences().applicationIds(Set.of("test-application-1.0.0"));
  }

  public static ApplicationDescriptor applicationDescriptor(String id) {
    return applicationDescriptor(id, APPLICATION_NAME, APPLICATION_VERSION)
      .addModulesItem(new Module().id("test-module-1.0.0").name("test-module").version("1.0.0"))
      .addModuleDescriptorsItem(new ModuleDescriptor().id("test-module-1.0.0"))
      .addUiModulesItem(new Module().id("test-ui-1.0.0").name("test-ui").version("1.0.0"))
      .addUiModuleDescriptorsItem(new ModuleDescriptor().id("test-ui-1.0.0"));
  }

  public static ApplicationDescriptor applicationDescriptor(String name, String version) {
    return new ApplicationDescriptor().id(id(name, version)).name(name).version(version);
  }

  public static ApplicationDescriptor applicationDescriptor(String id, String name, String version) {
    return new ApplicationDescriptor().id(id).name(name).version(version);
  }

  public static ApplicationEntity applicationDescriptorEntity() {
    return applicationDescriptorEntity(applicationDescriptor());
  }

  public static ApplicationEntity applicationDescriptorEntity(ApplicationDescriptor descriptor) {
    return applicationDescriptorEntity(descriptor, descriptor.getId());
  }

  public static ApplicationEntity applicationDescriptorEntity(ApplicationDescriptor descriptor,
    String appId) {
    var entity = new ApplicationEntity();
    entity.setId(Objects.requireNonNullElse(appId, APPLICATION_ID));
    entity.setName(descriptor.getName());
    entity.setVersion(descriptor.getVersion());
    entity.setApplicationDescriptor(descriptor);

    var mds = emptyIfNull(descriptor.getModuleDescriptors());
    var uimds = emptyIfNull(descriptor.getUiModuleDescriptors());

    emptyIfNull(descriptor.getModules()).forEach(module -> {
      var moduleEntity = new ModuleEntity();

      moduleEntity.setId(module.getId());
      moduleEntity.setName(module.getName());
      moduleEntity.setVersion(module.getVersion());

      var moduleDescriptor = mds.stream()
        .filter(md -> Objects.equals(module.getName() + '-' + module.getVersion(), md.getId()))
        .findFirst().orElse(null);

      moduleEntity.setDescriptor(moduleDescriptor);

      entity.addModule(moduleEntity);
    });

    emptyIfNull(descriptor.getUiModules()).forEach(module -> {
      var uiModuleEntity = new UiModuleEntity();

      uiModuleEntity.setId(module.getId());
      uiModuleEntity.setName(module.getName());
      uiModuleEntity.setVersion(module.getVersion());

      var moduleDescriptor = uimds.stream()
        .filter(md -> Objects.equals(module.getName() + '-' + module.getVersion(), md.getId()))
        .findFirst().orElse(null);

      uiModuleEntity.setDescriptor(moduleDescriptor);

      entity.addUiModule(uiModuleEntity);
    });

    return entity;
  }

  public static InterfaceReferenceEntity interfaceReferenceEntity(String moduleId, String id, String version,
    InterfaceReferenceEntity.ReferenceType type) {
    var entity = new InterfaceReferenceEntity();
    entity.setModuleId(moduleId);
    entity.setId(id);
    entity.setVersion(version);
    entity.setType(type);
    return entity;
  }

  public static DeploymentDescriptor deploymentDescriptor() {
    return new DeploymentDescriptor()
      .srvcId(SERVICE_ID)
      .instId(SERVICE_ID)
      .url(MODULE_URL);
  }

  public static DeploymentDescriptor deploymentDescriptor(String moduleId) {
    return new DeploymentDescriptor()
      .instId(moduleId)
      .srvcId(moduleId)
      .url("http://" + moduleId);
  }

  public static DeploymentDescriptor deploymentDescriptor(String moduleId, String url) {
    return new DeploymentDescriptor()
      .instId(moduleId)
      .srvcId(moduleId)
      .url(url);
  }

  public static Module module() {
    return new Module().id(APPLICATION_ID).version("1.7.9");
  }

  public static Module module(String name, String version) {
    return new Module().id(name + "-" + version).name(name).version(version);
  }

  public static Module module(String name, String version, String url) {
    return new Module().id(name + "-" + version).name(name).version(version).url(url);
  }

  public static ModuleBootstrap moduleBootstrap(ModuleBootstrapDiscovery module,
    ModuleBootstrapDiscovery... dependencies) {
    return new ModuleBootstrap()
      .module(module)
      .requiredModules(Arrays.asList(dependencies));
  }

  public static ModuleBootstrapDiscovery moduleBootstrapDiscovery(String moduleId, String interfaceId) {
    return new ModuleBootstrapDiscovery()
      .moduleId(moduleId)
      .applicationId(APPLICATION_ID)
      .location(moduleLocation(moduleId))
      .addInterfacesItem(moduleBootstrapInterface(interfaceId));
  }

  public static ModuleBootstrapInterface moduleBootstrapInterface(String interfaceId) {
    return new ModuleBootstrapInterface().id(interfaceId)
      .interfaceType("multiple")
      .addEndpointsItem(new ModuleBootstrapEndpoint().addMethodsItem("GET").path(interfacePath(interfaceId)));
  }

  public static ModuleBootstrapView moduleBootstrapView(String moduleId, String... interfaceIds) {
    var interfaceDescriptors = Arrays.stream(interfaceIds)
      .map(TestValues::interfaceDescriptor)
      .collect(Collectors.toList());

    var view = new ModuleBootstrapView();
    view.setId(moduleId);
    view.setLocation(moduleLocation(moduleId));
    view.setApplicationId(APPLICATION_ID);
    view.setDescriptor(new ModuleDescriptor().provides(interfaceDescriptors));
    return view;
  }

  public static InterfaceDescriptor interfaceDescriptor(String interfaceId) {
    return new InterfaceDescriptor().id(interfaceId).interfaceType("multiple")
      .addHandlersItem(new RoutingEntry().addMethodsItem("GET").path(interfacePath(interfaceId)));
  }

  public static InterfaceDescriptor interfaceDescriptor(String id, String version) {
    return new InterfaceDescriptor().id(id).version(version);
  }

  public static RoutingEntry routingEntry() {
    return new RoutingEntry().addMethodsItem("GET").path("/test-path").pathPattern("/test-pattern")
      .addPermissionsRequiredItem("item.get");
  }

  public static ApplicationDescriptor getApplicationDescriptor(String moduleId, String version) {
    var moduleDescriptor = new ModuleDescriptor()
      .id(moduleId)
      .description("test-module")
      .metadata(new AnyDescriptor().set("user", systemUser()));
    var uiModuleDescriptor = new ModuleDescriptor()
      .id("ui-module-1.0.0")
      .description("ui-module");
    var module = new Module()
      .id(moduleId)
      .name("test-module")
      .version(version);
    var uiModule = new Module()
      .name("ui-module")
      .version("1.0.0");

    return new ApplicationDescriptor()
      .id("test-0.1.1")
      .name("test")
      .version("0.1.1")
      .moduleDescriptors(List.of(moduleDescriptor))
      .modules(List.of(module))
      .uiModules(List.of(uiModule))
      .uiModuleDescriptors(List.of(uiModuleDescriptor));
  }

  public static ModuleDiscoveries emptyModuleDiscoveries() {
    return new ModuleDiscoveries().totalRecords(0L).discovery(emptyList());
  }

  public static ModuleDiscoveries moduleDiscoveries(ModuleDiscovery... moduleDiscoveries) {
    return new ModuleDiscoveries()
      .totalRecords((long) moduleDiscoveries.length)
      .discovery(List.of(moduleDiscoveries));
  }

  public static ModuleDiscovery moduleDiscovery() {
    return new ModuleDiscovery()
      .id(SERVICE_ID)
      .name(SERVICE_NAME)
      .version(SERVICE_VERSION)
      .location(MODULE_URL);
  }

  public static ModuleDiscovery moduleDiscovery(String name, String version, String location) {
    return new ModuleDiscovery()
      .id(name + "-" + version)
      .name(name)
      .version(version)
      .location(location);
  }

  public static ModuleDiscovery moduleFooDiscovery() {
    return new ModuleDiscovery()
      .id(MODULE_FOO_ID)
      .name(MODULE_FOO_NAME)
      .version(MODULE_FOO_VERSION)
      .location(MODULE_FOO_URL);
  }

  public static ModuleDiscovery moduleBarDiscovery() {
    return new ModuleDiscovery()
      .id(MODULE_BAR_ID)
      .name(MODULE_BAR_NAME)
      .version(MODULE_BAR_VERSION)
      .location(MODULE_BAR_URL);
  }

  public static ModuleEntity moduleEntity() {
    return moduleEntity(null);
  }

  public static ModuleEntity moduleEntity(String discoveryUrl) {
    var entity = new ModuleEntity();

    entity.setId(MODULE_ID);
    entity.setName(SERVICE_NAME);
    entity.setDiscoveryUrl(discoveryUrl);

    var application = applicationDescriptorEntity();
    application.addModule(entity);

    return entity;
  }

  public static ModuleDiscoveryEntity moduleDiscoveryEntity() {
    var entity = new ModuleDiscoveryEntity();

    entity.setId(MODULE_ID);
    entity.setName(SERVICE_NAME);
    entity.setVersion(SERVICE_VERSION);
    entity.setLocation(MODULE_URL);

    return entity;
  }

  private static String interfacePath(String interfaceId) {
    return String.format("/%s", interfaceId);
  }

  private static String moduleLocation(String moduleId) {
    return String.format("http://%s:8081", moduleId);
  }

  public static ValidationContext validationContext(ApplicationDescriptor applicationDescriptor) {
    return validationContext(applicationDescriptor, List.of(), List.of(), List.of());
  }

  public static ValidationContext validationContext() {
    return ValidationContext.builder()
      .applicationDescriptor(applicationDescriptor())
      .build();
  }

  public static ValidationContext validationContext(ApplicationDescriptor applicationDescriptor,
    List<ValidationMode> additionalModes) {
    return validationContext(applicationDescriptor, List.of(), List.of(), additionalModes);
  }

  public static ValidationContext validationContext(ApplicationDescriptor applicationDescriptor,
    List<ModuleDescriptor> loadedModuleDescriptors, List<ModuleDescriptor> loadedUiModuleDescriptors,
    List<ValidationMode> additionalModes) {
    return ValidationContext.builder()
      .applicationDescriptor(applicationDescriptor)
      .loadedModuleDescriptors(loadedModuleDescriptors)
      .loadedUiModuleDescriptors(loadedUiModuleDescriptors)
      .additionalModes(additionalModes)
      .build();
  }

  public static ModuleDescriptor moduleDescriptor(String name, String version) {
    return new ModuleDescriptor()
      .id(name + "-" + version)
      .description(name);
  }

  public static Map<String, Object> systemUser() {
    var userMap = new LinkedHashMap<String, Object>();
    userMap.put("type", "system");
    userMap.put("permissions", List.of("test.permission1", "test.permission2"));
    return userMap;
  }
}
