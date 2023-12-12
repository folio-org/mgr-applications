package org.folio.am.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationIdValidatorTest {

  @InjectMocks private ApplicationIdValidator applicationIdValidator;
  @Mock private ApplicationDescriptor applicationDescriptor;

  @Test
  void validate_positive_nullId() {
    when(applicationDescriptor.getId()).thenReturn(null);
    var validationContext = TestValues.validationContext(applicationDescriptor);

    applicationIdValidator.validate(validationContext);

    verify(applicationDescriptor).getId();
  }

  @Test
  void validate_positive_validId() {
    when(applicationDescriptor.getId()).thenReturn("test-1.0.0");
    when(applicationDescriptor.getArtifactId()).thenReturn("test-1.0.0");
    var validationContext = TestValues.validationContext(applicationDescriptor);

    applicationIdValidator.validate(validationContext);

    verify(applicationDescriptor).getId();
    verify(applicationDescriptor).getArtifactId();
  }

  @Test
  void validate_negative_invalidId() {
    when(applicationDescriptor.getId()).thenReturn("test-application");
    when(applicationDescriptor.getArtifactId()).thenReturn("test-1.0.0");
    var validationContext = TestValues.validationContext(applicationDescriptor);

    assertThatThrownBy(() -> applicationIdValidator.validate(validationContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application Descriptor id must be based on the name and version")
      .satisfies(error -> {
        var parameters = ((RequestValidationException) error).getErrorParameters();
        assertThat(parameters).isEqualTo(List.of(new Parameter().key("id").value("test-application")));
      });

    verify(applicationDescriptor).getId();
    verify(applicationDescriptor).getArtifactId();
  }
}
