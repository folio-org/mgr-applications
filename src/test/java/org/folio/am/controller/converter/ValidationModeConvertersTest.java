package org.folio.am.controller.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.am.domain.dto.ValidationMode;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class ValidationModeConvertersTest {

  private final ValidationModeConverters.FromString fromString = new ValidationModeConverters.FromString();
  private final ValidationModeConverters.ToString toString = new ValidationModeConverters.ToString();

  @ParameterizedTest
  @CsvSource({
    "default,  DEFAULT",
    "basic,    BASIC",
    "onCreate, ON_CREATE"
  })
  void fromString_positive_exactValue(String source, ValidationMode expected) {
    assertThat(fromString.convert(source)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "DEFAULT,  DEFAULT",
    "Default,  DEFAULT",
    "BASIC,    BASIC",
    "Basic,    BASIC",
    "oncreate, ON_CREATE",
    "ONCREATE, ON_CREATE",
    "ONcreate, ON_CREATE"
  })
  void fromString_positive_caseInsensitive(String source, ValidationMode expected) {
    assertThat(fromString.convert(source)).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(ValidationMode.class)
  void fromString_positive_everyGeneratedValueIsAccepted(ValidationMode mode) {
    assertThat(fromString.convert(mode.getValue())).isEqualTo(mode);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "none", "on_create", "ON_CREATE", "on create", "onCreate ", "unknown"})
  void fromString_negative_unexpectedValue(String source) {
    assertThatThrownBy(() -> fromString.convert(source))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unexpected value '" + source + "'");
  }

  @ParameterizedTest
  @EnumSource(ValidationMode.class)
  void toString_positive(ValidationMode mode) {
    assertThat(toString.convert(mode)).isEqualTo(mode.getValue());
  }

  @Test
  void roundTrip_positive_onCreate() {
    assertThat(fromString.convert(toString.convert(ValidationMode.ON_CREATE))).isEqualTo(ValidationMode.ON_CREATE);
  }
}
