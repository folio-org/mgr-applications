package org.folio.am.controller.converter;

import org.apache.commons.lang3.StringUtils;
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
      return ValidationMode.fromValue(StringUtils.lowerCase(source));
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
