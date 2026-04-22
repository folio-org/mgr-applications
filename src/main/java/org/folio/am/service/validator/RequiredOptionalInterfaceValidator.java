package org.folio.am.service.validator;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.REQUIRED_OPTIONAL_INTERFACE_VALIDATOR)
@BasicValidator
public class RequiredOptionalInterfaceValidator implements ApplicationValidator {

  @Override
  public void validate(ValidationContext validationContext) {
    var conflictingInterfaces = Stream.concat(
        validationContext.getAllModuleDescriptors().stream(),
        validationContext.getAllUiModuleDescriptors().stream())
      .map(this::findConflictParameter)
      .flatMap(Optional::stream)
      .toList();

    if (!conflictingInterfaces.isEmpty()) {
      throw new RequestValidationException("Interface cannot be both required and optional",
        conflictingInterfaces);
    }
  }

  private Optional<Parameter> findConflictParameter(ModuleDescriptor moduleDescriptor) {
    var conflictingInterfaceIds = toIds(moduleDescriptor.getRequires());
    conflictingInterfaceIds.retainAll(toIds(moduleDescriptor.getOptional()));

    if (conflictingInterfaceIds.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new Parameter().key(moduleDescriptor.getId()).value(conflictingInterfaceIds.toString()));
  }

  private static Set<String> toIds(List<InterfaceReference> interfaces) {
    return emptyIfNull(interfaces).stream()
      .map(InterfaceReference::getId)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
  }
}
