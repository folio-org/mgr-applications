package org.folio.am.domain.entity;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "module_interface_reference")
@Data
@IdClass(InterfaceReferenceKey.class)
public class InterfaceReferenceEntity {

  @Id
  @Column(name = "module_id")
  private String moduleId;

  @Id
  @Column
  private String id;

  @Column
  private String version;

  @Enumerated(EnumType.STRING)
  @Type(PostgreSQLEnumType.class)
  @Column(columnDefinition = "interface_ref_type", updatable = false)
  private ReferenceType type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "module_id", updatable = false, insertable = false)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private ModuleEntity module;

  public enum ReferenceType {
    PROVIDES, REQUIRES, OPTIONAL
  }
}
