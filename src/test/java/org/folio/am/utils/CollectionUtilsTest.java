package org.folio.am.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CollectionUtilsTest {

  @ParameterizedTest
  @MethodSource("unionDataProvider")
  @DisplayName("union_parameterized")
  void union_parameterized(Collection<Object> c1, Collection<Object> c2, List<Object> expected) {
    var result = CollectionUtils.union(c1, c2);
    assertThat(result).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> unionDataProvider() {
    return Stream.of(
      arguments(emptyList(), emptyList(), emptyList()),
      arguments(null, null, emptyList()),
      arguments(emptySet(), emptySet(), emptyList()),
      arguments(List.of(1, 2), null, List.of(1, 2)),
      arguments(null, List.of(1, 2), List.of(1, 2)),
      arguments(List.of(1, 2), List.of(2, 3), List.of(1, 2, 3))
    );
  }
}
