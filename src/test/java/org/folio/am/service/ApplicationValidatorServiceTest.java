package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.service.validator.ValidationMode.BASIC;
import static org.folio.am.service.validator.ValidationMode.ON_CREATE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.service.validator.ApplicationValidator;
import org.folio.am.service.validator.ValidationMode;
import org.folio.am.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationValidatorServiceTest {

  @Mock private TestBaseValidator basicValidator;
  @Mock private TestOnCreateValidator onCreateValidator;

  @Test
  void constructor_positive() {
    var service = new ApplicationValidatorService(List.of(basicValidator), List.of(onCreateValidator),
      BASIC);

    assertNotNull(service);
  }

  @Test
  void constructor_positive_noValidators() {
    var service = new ApplicationValidatorService(null, null, BASIC);
    assertNotNull(service);
  }

  @Test
  void constructor_negative_noMode() {
    var basicValidators = List.<ApplicationValidator>of(basicValidator);
    var onCreateValidators = List.<ApplicationValidator>of(onCreateValidator);

    assertThatThrownBy(() -> new ApplicationValidatorService(basicValidators, onCreateValidators,
      null))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining("Validation mode cannot be empty");
  }

  @Nested
  class ServiceWithBasicAndAdvancedValidators {

    private ApplicationValidatorService service;

    @BeforeEach
    void setUp() {
      service = new ApplicationValidatorService(List.of(basicValidator), List.of(onCreateValidator),
        ValidationMode.NONE);
    }

    @Test
    void validate_positive_noValidatorsCalled() {
      service.validate(TestValues.validationContext());

      verifyNoInteractions(basicValidator, onCreateValidator);
    }

    @Test
    void validate_positive_onlyBasicValidatorCalled() {
      service.validate(TestValues.validationContext(), BASIC);

      verify(basicValidator).validate(TestValues.validationContext());
      verifyNoInteractions(onCreateValidator);
    }

    @Test
    void validate_positive_bothValidatorsCalled() {
      var ctx = TestValues.validationContext(TestValues.applicationDescriptor(), List.of(ON_CREATE));
      service.validate(ctx, BASIC);

      verify(basicValidator).validate(ctx);
      verify(onCreateValidator).validate(ctx);
    }

    @Test
    void validate_basicGoesFirst() {
      var ctx = TestValues.validationContext(TestValues.applicationDescriptor(), List.of(ON_CREATE));
      var ex = new RequestValidationException("Invalid");
      doThrow(ex).when(basicValidator).validate(ctx);

      assertThatThrownBy(() -> service.validate(ctx, BASIC))
        .isInstanceOf(ex.getClass())
        .hasMessage(ex.getMessage());

      verifyNoInteractions(onCreateValidator);
    }

    @Test
    void validate_validatorsOrder() {
      var descriptor = TestValues.applicationDescriptor();
      var context = TestValues.validationContext(descriptor, List.of(ON_CREATE));

      service.validate(context, BASIC);

      var inorder = inOrder(basicValidator, onCreateValidator);
      inorder.verify(basicValidator).validate(context);
      inorder.verify(onCreateValidator).validate(context);
    }
  }

  @Order(1)
  interface TestBaseValidator extends ApplicationValidator {

  }

  @Order(2)
  interface TestOnCreateValidator extends ApplicationValidator {

  }
}
