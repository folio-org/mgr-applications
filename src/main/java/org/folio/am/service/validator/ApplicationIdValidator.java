package org.folio.am.service.validator;

import org.apache.commons.lang3.StringUtils;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.APPLICATION_VALIDATOR)
@BasicValidator
public class ApplicationIdValidator implements ApplicationValidator {

  @Override
  public void validate(ValidationContext context) {
    var descriptor = context.getApplicationDescriptor();
    var descriptorId = descriptor.getId();
    var artifactId = descriptor.getArtifactId();
    if (StringUtils.isNotBlank(descriptorId) && !descriptorId.equals(artifactId)) {
      throw new RequestValidationException(
        "Application Descriptor id must be based on the name and version", "id", descriptorId);
    }
  }
}
