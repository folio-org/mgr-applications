package org.folio.am.service;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ClassUtils.getShortClassName;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.springframework.core.annotation.OrderUtils.getOrder;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.service.validator.ApplicationValidator;
import org.folio.am.service.validator.BasicValidator;
import org.folio.am.service.validator.OnCreateValidator;
import org.folio.am.service.validator.ValidationMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ApplicationValidatorService {

  private static final int DEFAULT_VALIDATOR_ORDER = Integer.MAX_VALUE;

  private final List<ApplicationValidator> basicValidators;
  private final List<ApplicationValidator> onCreateValidators;
  private final ValidationMode defaultMode;

  public ApplicationValidatorService(
    @BasicValidator List<ApplicationValidator> basicValidators,
    @OnCreateValidator List<ApplicationValidator> onCreateValidators,
    @Value("${application.validation.default-mode}") ValidationMode defaultValidationMode) {

    this.basicValidators = emptyIfNull(basicValidators);
    this.onCreateValidators = emptyIfNull(onCreateValidators);

    requireNonNull(defaultValidationMode, () -> "Validation mode cannot be empty. One of the following required: "
      + stream(ValidationMode.values()).map(mode -> mode.name().toLowerCase()).collect(joining(", ")));

    this.defaultMode = defaultValidationMode;

    log.debug("Application validator service initialized: defaultMode = {}, basicValidators = {}, "
      + "onCreateValidators = {}", defaultMode, names(this.basicValidators), names(this.onCreateValidators));
  }

  /**
   * Runs default validations for the given {@link ApplicationDescriptor} object.
   *
   * @param context - validation context
   * @throws RequestValidationException - if given descriptor does not satisfy any validator
   */
  public void validate(ValidationContext context) {
    validate(context, defaultMode);
  }

  /**
   * Runs validations of the specified {@link ValidationMode} mode for the given {@link ApplicationDescriptor} object.
   *
   * @param context - validation context
   * @param mode - validation mode
   * @throws RequestValidationException - if given context does not satisfy any validator
   */
  public void validate(ValidationContext context, ValidationMode mode) {
    log.info("Validating application context: id = {}, validationMode = {}", context.getApplicationDescriptor().getId(),
      mode);

    concat(getValidators(mode), extractAdditionalValidators(context.getAdditionalModes()))
      .forEach(validator -> validator.validate(context));
  }

  private List<ApplicationValidator> extractAdditionalValidators(List<ValidationMode> modes) {
    return toStream(modes).map(this::getValidators).flatMap(List::stream).collect(toList());
  }

  private List<ApplicationValidator> getValidators(ValidationMode type) {
    return switch (type) {
      case NONE -> emptyList();
      case BASIC -> basicValidators;
      case ON_CREATE -> onCreateValidators;
    };
  }

  @SafeVarargs
  private static List<ApplicationValidator> concat(List<ApplicationValidator>... validators) {
    return stream(validators)
      .flatMap(List::stream)
      .distinct()
      .sorted(comparingInt(validator -> getOrder(validator.getClass(), DEFAULT_VALIDATOR_ORDER)))
      .collect(toList());
  }

  private static String names(List<ApplicationValidator> validators) {
    assert nonNull(validators);

    return validators.stream()
      .map(validator -> getShortClassName(validator.getClass()))
      .collect(joining(", ", "[", "]"));
  }
}
