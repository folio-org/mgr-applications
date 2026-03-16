package org.folio.am.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.Immutable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_application_with_discovery")
public class ApplicationDiscoveryView extends ArtifactEntity {

  @OrderBy("id")
  @OneToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "application_module",
    joinColumns = @JoinColumn(name = "application_id"),
    inverseJoinColumns = @JoinColumn(name = "module_id")
  )
  @ToString.Exclude
  private Set<ModuleDiscoveryEntity> moduleDiscoveries = new TreeSet<>(idComparator());

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    Class<?> otherEffectiveClass = o instanceof HibernateProxy
      ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
    Class<?> thisEffectiveClass = this instanceof HibernateProxy
      ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
    if (thisEffectiveClass != otherEffectiveClass) {
      return false;
    }
    ApplicationDiscoveryView that = (ApplicationDiscoveryView) o;
    return getId() != null && Objects.equals(getId(), that.getId());
  }

  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy
      ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
  }
}
