package org.folio.am.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.Immutable;

@Data
@Entity
@Immutable
@Table(name = "module_bootstrap")
public class ModuleBootstrapView {

  @Id
  private String id;

  @Column(name = "application_id")
  private String applicationId;

  @Column(name = "discovery_url")
  private String location;

  @Type(JsonBinaryType.class)
  @Column(name = "descriptor", columnDefinition = "jsonb")
  @EqualsAndHashCode.Exclude
  private ModuleDescriptor descriptor;
}
