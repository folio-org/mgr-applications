package org.folio.am.service.validator;

import static java.util.Objects.isNull;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.SPACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.INTERFACE_NAME_VALIDATOR)
@BasicValidator
public class InterfaceNameValidator implements ApplicationValidator {

  private static final Pattern ID_PATTERN = compile("^[A-Za-z0-9._-]+$");
  private static final Pattern VERSION_PATTERN = compile("^(\\d+\\.\\d+)$");

  @Override
  public void validate(ValidationContext validationContext) {
    var notValidInterfaces = new HashMap<String, List<String>>();
    validationContext.getAllModuleDescriptors()
      .forEach(descriptor -> validateInterfaces(descriptor.getProvides(),   notValidInterfaces, descriptor.getId()));

    if (isEmpty(notValidInterfaces)) {
      return;
    }

    throw new RequestValidationException("Invalid provided interface name or version",
      mapToParameters(notValidInterfaces));
  }

  private void validateInterfaces(List<InterfaceDescriptor> providedInterfaces,
    Map<String, List<String>> notValidInterfacesByType, String descriptorId) {
    emptyIfNull(providedInterfaces).forEach(item -> {
      var id = item.getId();
      var version = item.getVersion();
      if (isNotValid(id, version)) {
        var interfaceName = extractInterfaceName(id, version);
        addNotValidInterfaceToReport(notValidInterfacesByType, descriptorId, interfaceName);
      }
    });
  }

  private void addNotValidInterfaceToReport(Map<String, List<String>> notValidInterfacesByType, String descriptorId,
    String interfaceName) {
    notValidInterfacesByType.putIfAbsent(descriptorId, new ArrayList<>());
    notValidInterfacesByType.computeIfPresent(descriptorId, (key, value) -> {
      value.add(interfaceName);
      return value;
    });
  }

  private static boolean isNotValid(String interfaceId, String interfaceVersion) {
    if (isNull(interfaceId) || !ID_PATTERN.matcher(interfaceId).matches()) {
      return true;
    }
    return isNull(interfaceVersion) || !VERSION_PATTERN.matcher(interfaceVersion).matches();
  }

  private static String extractInterfaceName(String id, String version) {
    return id + SPACE + version;
  }

  private static List<Parameter> mapToParameters(Map<String, List<String>> notValidInterfacesByType) {
    return notValidInterfacesByType.entrySet().stream()
      .map(entry -> new Parameter().key(entry.getKey()).value(entry.getValue().toString()))
      .collect(toList());
  }
}
