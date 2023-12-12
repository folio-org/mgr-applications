package org.folio.am.service.validator;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.Collectors.toLinkedHashMap;

import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.service.ApplicationService;
import org.folio.common.domain.model.error.Parameter;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.APPLICATION_DESCRIPTOR_VALIDATOR)
@BasicValidator
@RequiredArgsConstructor
public class ApplicationModuleValidator implements ApplicationValidator {

  private final ApplicationService service;

  @Override
  public void validate(ValidationContext context) {
    var descriptor = context.getApplicationDescriptor();
    var moduleIds = mapItems(descriptor.getModules(), Module::getArtifactId);
    var applications = service.findApplicationsByModuleIds(moduleIds);

    var notValidApplications = applications.stream()
      .filter(not(sameApplicationNameAs(descriptor)))
      .collect(toLinkedHashMap(ApplicationDescriptor::getName, desc -> getModuleIdsContaining(desc, moduleIds)));

    if (isNotEmpty(notValidApplications)) {
      var parameters = mapItems(notValidApplications.entrySet(), ApplicationModuleValidator::buildParameter);
      throw new RequestValidationException("Modules belong to other Applications", parameters);
    }
  }

  private static Parameter buildParameter(Entry<String, List<String>> entry) {
    return new Parameter().key(entry.getKey()).value(entry.getValue().toString());
  }

  private static Predicate<ApplicationDescriptor> sameApplicationNameAs(ApplicationDescriptor descriptor) {
    return app -> Objects.equals(descriptor.getName(), app.getName());
  }

  private static List<String> getModuleIdsContaining(ApplicationDescriptor descriptor, List<String> moduleIds) {
    return toStream(descriptor.getModules())
      .map(Module::getArtifactId)
      .filter(moduleIds::contains)
      .collect(toList());
  }
}
