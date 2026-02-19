package org.folio.am.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.hibernate.annotations.Type;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.Immutable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_module_bootstrap")
public class ModuleBootstrapView {

  @Id
  private String id;

  @Column(name = "application_id")
  private String applicationId;

  @Column(name = "discovery_url")
  private String location;

  @Column(name = "system_user_required", nullable = false)
  private boolean systemUserRequired;

  @Type(JsonBinaryType.class)
  @Column(name = "descriptor", columnDefinition = "jsonb")
  @EqualsAndHashCode.Exclude
  private ModuleDescriptor descriptor;

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
    ModuleBootstrapView that = (ModuleBootstrapView) o;
    return getId() != null && Objects.equals(getId(), that.getId());
  }

  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy
      ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
  }
}
