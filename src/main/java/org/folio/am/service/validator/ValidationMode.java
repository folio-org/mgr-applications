package org.folio.am.service.validator;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.regex.Pattern;

public enum ValidationMode {
  NONE,
  BASIC,
  ON_CREATE;

  private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("(?<=[a-z])[A-Z]");

  public static ValidationMode fromValue(String value) {
    var underscoreValue = camelCaseToUnderscore(value);

    for (var e : ValidationMode.values()) {
      if (e.name().equalsIgnoreCase(defaultString(underscoreValue))) {
        return e;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  private static String camelCaseToUnderscore(String value) {
    if (isBlank(value)) {
      return value;
    }

    var m = CAMEL_CASE_PATTERN.matcher(value);
    return m.replaceAll(match -> "_" + match.group().toLowerCase());
  }
}
