package org.folio.am.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@Entity
@Table(name = "module")
@EqualsAndHashCode(callSuper = true)
@SQLRestriction("discovery_url IS NOT NULL")
public class ModuleDiscoveryEntity extends ArtifactEntity {

  @Column(name = "discovery_url")
  private String location;
}
