package org.folio.am.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface Artifact {

  /**
   * Returns the artifact name.
   *
   * @return artifact name as {@link String} object.
   */
  String getName();

  /**
   * Returns the artifact version.
   *
   * @return artifact version as {@link String} object.
   */
  String getVersion();

  /**
   * Creates service id from artifact name and version.
   *
   * @return created artifact id as {@link String} object
   */
  @JsonIgnore
  default String getArtifactId() {
    var name = getName();
    var version = getVersion();
    return name + "-" + version;
  }
}
