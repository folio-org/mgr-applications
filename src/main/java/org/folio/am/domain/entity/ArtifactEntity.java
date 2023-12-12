package org.folio.am.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Comparator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@MappedSuperclass
public abstract class ArtifactEntity {

  @Id
  protected String id;

  @Column(name = "version")
  protected String version;

  @Column(name = "name")
  protected String name;

  public static Comparator<ArtifactEntity> idComparator() {
    return (first, second) -> StringUtils.compare(first.getId(), second.getId());
  }
}
