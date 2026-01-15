package org.folio.am.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CollectionUtilsTest {

  @ParameterizedTest
  @MethodSource("unionDataProvider")
  void union_positive(Collection<Object> c1, Collection<Object> c2, List<Object> expected) {
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

  @ParameterizedTest
  @MethodSource("filterAndMapToSetDataProvider")
  void filterAndMapToSet_positive(
      Collection<Integer> source,
      Predicate<Integer> predicate,
      Function<Integer, String> mapper,
      Set<String> expected
  ) {
    var result = CollectionUtils.filterAndMapToSet(source, predicate, mapper);
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> filterAndMapToSetDataProvider() {
    Predicate<Integer> isEven = i -> i % 2 == 0;
    Predicate<Integer> greaterThanOne = i -> i > 1;
    Function<Integer, String> toString = Object::toString;

    return Stream.of(
      // Empty collection
      arguments(emptyList(), isEven, toString, emptySet()),
      // Null collection
      arguments(null, isEven, toString, emptySet()),
      // Filter even numbers and map to string
      arguments(
        List.of(1, 2, 3, 4, 5),
        isEven,
        toString,
        Set.of("2", "4")
      ),
      // No matches
      arguments(
        List.of(1, 3, 5),
        isEven,
        toString,
        emptySet()
      ),
      // All match
      arguments(
        List.of(2, 4, 6),
        isEven,
        toString,
        Set.of("2", "4", "6")
      ),
      // Duplicates are removed (set behavior)
      arguments(
        List.of(1, 2, 2, 3, 3),
        greaterThanOne,
        toString,
        Set.of("2", "3")
      )
    );
  }

  @Test
  void filterAndMap_positive_returnsListWithDuplicates() {
    // Verify existing filterAndMap returns List (not Set)
    var result = CollectionUtils.filterAndMap(
      List.of(1, 2, 2, 3, 3),
      i -> i > 1,
      Object::toString
    );

    assertThat(result).containsExactly("2", "2", "3", "3");
    assertThat(result).isInstanceOf(List.class);
  }
}
