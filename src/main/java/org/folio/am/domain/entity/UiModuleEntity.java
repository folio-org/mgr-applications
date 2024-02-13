package org.folio.am.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "ui_module")
@EqualsAndHashCode(callSuper = true)
public class UiModuleEntity extends ArtifactEntity {

  @Type(JsonBinaryType.class)
  @Column(name = "descriptor", columnDefinition = "jsonb")
  @EqualsAndHashCode.Exclude
  private ModuleDescriptor descriptor;

  @ManyToMany(mappedBy = "uiModules")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Set<ApplicationEntity> applications = new HashSet<>();

  public static UiModuleEntity of(String id) {
    var entity = new UiModuleEntity();
    entity.id = id;
    return entity;
  }
}
