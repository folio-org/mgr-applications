package org.folio.am.utils;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionUtils {

  public static <T> Stream<T> toStream(Collection<T> source) {
    return emptyIfNull(source).stream();
  }

  /**
   * Collects given collection to set using mapping function.
   *
   * @param source - source collection to process
   * @param mapper - java {@link Function} mapping function
   * @param <T> - generic type for incoming collection element
   * @param <R> - generic type for output collection element
   * @return - created {@link Set} object
   */
  public static <T, R> Set<R> mapItemsToSet(Collection<T> source, Function<? super T, ? extends R> mapper) {
    return toStream(source).map(mapper).collect(Collectors.toSet());
  }

  public static <T> T takeOne(Collection<T> source) {
    return takeOne(source,
      () -> new NoSuchElementException("Collection is empty"),
      () -> new NoSuchElementException("Collection contains more than one element: count = " + source.size()));
  }

  public static <T> T takeOne(Collection<T> source, Supplier<? extends RuntimeException> emptyCollectionExcSupplier,
      Supplier<? extends RuntimeException> tooManyItemsExcSupplier) {
    if (isEmpty(source)) {
      throw emptyCollectionExcSupplier.get();
    }

    if (source.size() > 1) {
      throw tooManyItemsExcSupplier.get();
    }

    return source.iterator().next();
  }

  public static <T> Optional<T> findOne(Collection<T> source) {
    return emptyIfNull(source).size() == 1 ? Optional.ofNullable(source.iterator().next()) : Optional.empty();
  }

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
      .collect(toList());
  }
}
