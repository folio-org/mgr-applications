package org.folio.am.service.validator;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.keycloak.common.util.CollectionUtil.isNotEmpty;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.MODULE_DESCRIPTOR_SOURCE_VALIDATOR)
@OnCreateValidator
public class ModuleDescriptorsSourceValidator implements ApplicationValidator {

  @Override
  public void validate(ValidationContext context) {
    var applicationDescriptor = context.getApplicationDescriptor();
    var notValidModules =
      findNotValidModules(applicationDescriptor.getModules(), applicationDescriptor.getModuleDescriptors());
    var notValidUiModules =
      findNotValidModules(applicationDescriptor.getUiModules(), applicationDescriptor.getUiModuleDescriptors());

    if (isEmpty(notValidModules) && isEmpty(notValidUiModules)) {
      return;
    }

    var parameters = buildErrorParams(notValidModules, notValidUiModules);
    throw new RequestValidationException("Validation failed. Modules are not valid by URL and module descriptors.",
      parameters);
  }

  private static List<Parameter> buildErrorParams(List<String> notValidModules, List<String> notValidUiModules) {
    var parameters = new ArrayList<Parameter>();
    if (isNotEmpty(notValidModules)) {
      parameters.add(new Parameter().key("modules").value(notValidModules.toString()));
    }
    if (isNotEmpty(notValidUiModules)) {
      parameters.add(new Parameter().key("uiModules").value(notValidUiModules.toString()));
    }
    return parameters;
  }

  private static List<String> findNotValidModules(List<Module> modules, List<ModuleDescriptor> descriptors) {
    return toStream(modules)
      .filter(module -> isNotValid(descriptors, module, module.getUrl()))
      .map(Module::getArtifactId)
      .collect(toList());
  }

  private static boolean isNotValid(List<ModuleDescriptor> descriptors, Module module, String url) {
    return urlAndModuleDescriptorExist(descriptors, module, url)
      || urlAndModuleDescriptorNotExist(descriptors, module, url);
  }

  private static boolean urlAndModuleDescriptorNotExist(List<ModuleDescriptor> descriptors, Module module, String url) {
    return isBlank(url) && !verifyDescriptorExistenceById(module.getName(), module.getVersion(), descriptors);
  }

  private static boolean urlAndModuleDescriptorExist(List<ModuleDescriptor> descriptors, Module module, String url) {
    return isNotBlank(url) && verifyDescriptorExistenceById(module.getName(), module.getVersion(), descriptors);
  }

  private static boolean verifyDescriptorExistenceById(String name, String version,
    List<ModuleDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      return false;
    }
    return descriptors.stream()
      .anyMatch(moduleDescriptor -> StringUtils.equals(moduleDescriptor.getId(), name + "-" + version));
  }
}
