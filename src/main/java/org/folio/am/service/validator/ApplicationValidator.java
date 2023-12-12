package org.folio.am.service.validator;

import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;

public interface ApplicationValidator {

  /**
   * Runs validation for given {@link ValidationContext} object.
   *
   * @param validationContext - validation context
   * @throws RequestValidationException - if given descriptor does not satisfy validator
   */
  void validate(ValidationContext validationContext);
}
