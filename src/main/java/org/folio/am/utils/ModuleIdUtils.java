package org.folio.am.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class ModuleIdUtils {

  public static Pair<String, String> getNameAndVersion(String moduleId) {
    if (moduleId == null) {
      return null;
    }

    var indexOfSeparator = moduleId.lastIndexOf("-");
    return Pair.<String, String>of(moduleId.substring(0, indexOfSeparator), moduleId.substring(indexOfSeparator + 1));
  }
}
