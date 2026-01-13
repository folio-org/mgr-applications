package org.folio.am.domain.entity;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "application")
@EqualsAndHashCode(callSuper = true)
public class ApplicationEntity extends ArtifactEntity {

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb", name = "application_descriptor")
  @EqualsAndHashCode.Exclude
  private ApplicationDescriptor applicationDescriptor;

  @OrderBy("id")
  @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinTable(name = "application_module",
    joinColumns = @JoinColumn(name = "application_id"),
    inverseJoinColumns = @JoinColumn(name = "module_id")
  )
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<ModuleEntity> modules = new TreeSet<>(idComparator());

  public static ApplicationEntity of(String id) {
    var entity = new ApplicationEntity();
    entity.id = id;
    return entity;
  }

  public void setModules(Set<ModuleEntity> newModules) {
    removeAllModules();

    emptyIfNull(newModules).forEach(this::addModule);
  }

  public void addModule(ModuleEntity module) {
    if (module.getId() == null) {
      module.setId(module.getName() + "-" + module.getVersion());
    }
    module.getApplications().add(this);

    modules.add(module);
  }

  public void removeAllModules() {
    removeAllModules(module -> {});
  }

  public void removeAllModules(Consumer<ModuleEntity> onModuleRemoved) {
    for (var itr = modules.iterator(); itr.hasNext(); ) {
      var module = itr.next();

      module.getApplications().remove(this);
      itr.remove();

      onModuleRemoved.accept(module);
    }
  }
}
