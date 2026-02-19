package org.folio.am.domain.entity;

/**
 * Projection interface for Application artifact with only id, name, and version fields.
 *
 * <p>Used to fetch minimal application data without loading full entity.
 */
public interface ApplicationProjection {

  String getId();

  String getName();

  String getVersion();
}
