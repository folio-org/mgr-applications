package org.folio.am.domain.validation.constraints;

import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SemVersionOrRangeValidatorTest {

  @Mock
  private ConstraintValidatorContext validatorContext;
  private final SemVersionOrRangeValidator validator = new SemVersionOrRangeValidator();

  @ParameterizedTest
  @MethodSource("validVersionOrRanges")
  void isValid_positive(String value) {
    var result = validator.isValid(value, validatorContext);

    Assertions.assertTrue(result);
  }

  @ParameterizedTest
  @MethodSource("notValidVersionOrRanges")
  void isValid_negative(String value) {
    var result = validator.isValid(value, validatorContext);

    Assertions.assertFalse(result);
  }

  private static List<String> validVersionOrRanges() {
    return List.of(
      // versions
      "1.0.0",
      "1.0.1",
      "1.2.4",
      "2.1.0",
      "2.1.1",
      "3.2.1",
      "4.1.0",
      "4.0.0-alpha.1",
      "1.2.3-beta",
      "2.0.0+build456",
      "1.1.0-alpha.2+build789",
      "2.3.0-beta.1+build42",
      "2.0.0-alpha",
      "3.0.0+build123",
      "3.2.2-beta",
      "4.0.0+build987",
      "2.0.0-alpha.3+build555",
      "3.0.0+build999",
      "1.0.0-alpha.4",
      "3.3.0+build111",
      // basic range
      ">=2.0.0",
      "<3.0.0",
      "<=4.0.0",
      ">5.0.0",
      ">=1.2.3 <4.5.6",
      ">2.3.4 <5.6.7",
      "<3.4.5 >6.7.8",
      // Hyphen Range
      "1.0.0 - 2.0.0",
      "2.1.0 - 3.1.0",
      "4.0.0 - 5.0.0",
      "1.0.0 - 4.0.0",
      // X-Range
      "1.x",
      "1.2.x",
      "2.x",
      "3.0.x",
      "2.3.x",
      "3.4.x",
      "4.0.x",
      // Tilde Range
      "~1.0.0",
      "~2.1.0",
      "~3.2.0",
      "~1.2.3",
      "~2.3.4",
      // Caret Range
      "^1.0.0",
      "^2.0.0",
      "^1.2.3",
      "^2.3.4",
      "^3.4.5",
      "^4.1.0",
      // Combined with '||'
      "1.0.0 || >=2.0.0 <3.0.0",
      "<=2.0.0 || >=4.0.0",
      "1.2.3 - 2.3.4 || 3.0.0 - 4.0.0",
      "<3.0.0 || >5.0.0 || 6.0.0 - 7.0.0",
      ">=1.0.0 <4.0.0 || 5.0.0",
      // Ivy ranges
      "[1.0,)",
      "]1.0,)",
      "(,2.0]",
      "(,2.0[",
      "[1.0,2.0]",
      "[2.0,3.0[",
      "[3.0,4.0]",
      "[4.0,5.0)",
      "[5.0,6.0)",
      "[1.0,2.0)",
      "[2.0,3.0]",
      "[5.0,6.0]",
      "(1.0,2.0]",
      "(2.0,3.0[",
      "(3.0,4.0]",
      "(5.0,6.0]"
    );
  }

  private static List<String> notValidVersionOrRanges() {
    return List.of(
      // Invalid Semantic Versions
      "2.3.",
      "alpha.1",
      "1.2.3-beta.",
      "1.2.3+build.",
      // Invalid Regular Ranges:
      // ">=2.0.0 <!1.0.0",
      //"<1.0.0 >>2.0.0",
      "+>1.2.3-beta.1+build",
      // Invalid Hyphen Ranges:
      //">=2.0.0 - <1.0.0",
      //"2.3.4 - 2.3.4 - 4.5.6",
      //"1.0.0 - 2.0.0 - 3.0.0",
      //"2.3.4 - ",
      // Invalid X-Ranges:
      //"1.2",
      "3.4.x.5",
      "1.2.x.x",
      // Invalid Tilde Ranges:
      "~2.1.0.0",
      "1.2.3.4~",
      "1.2~.3",
      // Invalid Caret Ranges:
      "^2.1.0.0",
      "1.2.3.4^",
      "1.2^",
      "^1.2.3.4+build",
      // Invalid Ivy Version Matchers:
      "[1.0,2.0",
      "2.0,3.0]",
      "(5.0,6.0"
    );
  }
}
