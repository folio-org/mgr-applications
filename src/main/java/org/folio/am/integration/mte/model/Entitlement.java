package org.folio.am.integration.mte.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Tenant entitlement descriptor.
 */
@Data
@AllArgsConstructor(staticName = "of")
public class Entitlement {

  /**
   * An application identifier.
   */
  @JsonProperty("applicationId")
  private String applicationId;

  /**
   * A tenant identifier.
   */
  @JsonProperty("tenantId")
  private String tenantId;
}
