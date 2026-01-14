package org.folio.am.domain.entity;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "module")
@EqualsAndHashCode(callSuper = true)
public class ModuleEntity extends ArtifactEntity {

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type", columnDefinition = "module_type")
  private ModuleType type;

  @Type(JsonBinaryType.class)
  @Column(name = "descriptor", columnDefinition = "jsonb")
  @EqualsAndHashCode.Exclude
  private ModuleDescriptor descriptor;

  @Column(name = "discovery_url")
  @EqualsAndHashCode.Exclude
  private String discoveryUrl;

  @OneToMany(cascade = CascadeType.ALL,
    fetch = FetchType.LAZY,
    mappedBy = "module",
    orphanRemoval = true)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Set<InterfaceReferenceEntity> interfaces = new HashSet<>();

  @ManyToMany(mappedBy = "modules")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Set<ApplicationEntity> applications = new HashSet<>();

  public static ModuleEntity of(String id, ModuleType type) {
    requireNonNull(id, "Module id must not be null");
    requireNonNull(type, "Module type must not be null");

    var entity = new ModuleEntity();
    entity.id = id;
    entity.type = type;
    return entity;
  }

  public void setInterfaces(Collection<InterfaceReferenceEntity> newInterfaces) {
    removeExistingInterfaces();

    emptyIfNull(newInterfaces).forEach(this::addInterface);
  }

  public void addInterface(InterfaceReferenceEntity interfaceEntity) {
    interfaceEntity.setModuleId(id);
    interfaceEntity.setModule(this);

    interfaces.add(interfaceEntity);
  }

  public boolean isBackendModule() {
    return ModuleType.BACKEND.equals(type);
  }

  public boolean isUiModule() {
    return ModuleType.UI.equals(type);
  }

  private void removeExistingInterfaces() {
    for (var itr = interfaces.iterator(); itr.hasNext(); ) {
      var i = itr.next();

      i.setModuleId(null);
      i.setModule(null);
      itr.remove();
    }
  }
}
