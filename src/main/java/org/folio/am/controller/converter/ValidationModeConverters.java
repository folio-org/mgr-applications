package org.folio.am.controller.converter;

import java.util.Arrays;
import org.folio.am.domain.dto.ValidationMode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

public final class ValidationModeConverters {

  private ValidationModeConverters() {
  }

  @Component
  public static class FromString implements Converter<String, ValidationMode> {

    @Override
    public ValidationMode convert(String source) {
      return Arrays.stream(ValidationMode.values())
        .filter(mode -> mode.getValue().equalsIgnoreCase(source))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unexpected value '" + source + "'"));
    }
  }

  @Component
  public static class ToString implements Converter<ValidationMode, String> {

    @Override
    public String convert(ValidationMode source) {
      return source.getValue();
    }
  }
}
