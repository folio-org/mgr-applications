package org.folio.am.utils;

import static java.util.stream.Collectors.toSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionUtils {

  /**
   * Unions 2 collections, leaving only unique values and returns it as {@link List} object.
   *
   * @param collection1 - first {@link Collection} object
   * @param collection2 - second {@link Collection} object
   * @param <T> - generic type for collection element
   * @return merged collections as {@link List} object
   */
  public static <T> List<T> union(Collection<T> collection1, Collection<T> collection2) {
    return Stream.concat(toStream(collection1), toStream(collection2))
      .distinct()
      .toList();
  }

  /**
   * Filters a collection based on a predicate and maps the elements to another type using a mapper function.
   * The resulting elements are returned as a list.
   *
   * @param source - collection to filter and map
   * @param predicate - predicate to apply for filtering
   * @param mapper - function to map filtered elements to another type
   * @param <T> - type of elements in the source collection
   * @param <R> - type of elements in the resulting list
   * @return - list of mapped elements that match the predicate
   */
  public static <T, R> List<R> filterAndMap(Collection<T> source, Predicate<T> predicate, Function<T, R> mapper) {
    return toStream(source).filter(predicate).map(mapper).toList();
  }

  /**
   * Filters a collection based on a predicate and maps the elements to another type using a mapper function.
   * The resulting elements are collected into a set to ensure uniqueness.
   *
   * @param source - collection to filter and map
   * @param predicate - predicate to apply for filtering
   * @param mapper - function to map filtered elements to another type
   * @param <T> - type of elements in the source collection
   * @param <R> - type of elements in the resulting set
   * @return - set of mapped elements that match the predicate
   */
  public static <T, R> Set<R> filterAndMapToSet(Collection<T> source, Predicate<T> predicate, Function<T, R> mapper) {
    return toStream(source).filter(predicate).map(mapper).collect(toSet());
  }
}
