package org.folio.am.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
public class ModuleIdUtilsTest {

  @Test
  void testParse_positive() {
    var result = ModuleIdUtils.getNameAndVersion("a-b-c-1.2.3");
    assertThat(result.getLeft()).isEqualTo("a-b-c");
    assertThat(result.getRight()).isEqualTo("1.2.3");
  }

  @Test
  void testParse_null() {
    var result = ModuleIdUtils.getNameAndVersion(null);
    assertThat(result).isNull();
  }
}
