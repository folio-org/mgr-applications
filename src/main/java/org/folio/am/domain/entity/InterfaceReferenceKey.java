package org.folio.am.domain.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class InterfaceReferenceKey implements Serializable {

  /**
   * Module id.
   */
  private String moduleId;

  /**
   * Reference to interface id.
   */
  private String id;
}
