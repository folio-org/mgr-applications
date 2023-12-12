package org.folio.am.domain.validation.constraints;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.log4j.Log4j2;
import org.semver4j.RangesListFactory;
import org.semver4j.Semver;

@Log4j2
class SemVersionOrRangeValidator implements ConstraintValidator<SemVersionOrRange, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    log.debug("Checking if provided value represents a valid semantic version or a range: {}", value);
    // Jakarta Bean Validation specification recommends to consider null values as being valid.
    // If null is not a valid value for an element, it should be annotated with @NotNull explicitly.
    return value == null || isValidVersion(value) || isValidRange(value);
  }

  private boolean isValidVersion(String value) {
    var result = Semver.isValid(value);

    if (!result) {
      log.debug("Provided value is not a valid semantic version: {}", value);
    }

    return result;
  }

  private boolean isValidRange(String value) {
    var ranges = RangesListFactory.create(value);
    var result = isNotEmpty(ranges.get());

    if (!result) {
      log.debug("Provided value is not a valid semantic version range(s): {}", value);
    } else {
      log.debug("Parsed ranges: {}", ranges);
    }

    return result;
  }
}
