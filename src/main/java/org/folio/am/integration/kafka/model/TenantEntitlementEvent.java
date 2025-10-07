package org.folio.am.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class TenantEntitlementEvent {
  @JsonProperty("moduleId")
  private String moduleId;

  @JsonProperty("tenantName")
  private String tenantName;

  @JsonProperty("tenantId")
  private UUID tenantId;

  @JsonProperty("type")
  private Type type;

  public enum Type {
    ENTITLE,
    UPGRADE,
    REVOKE
  }
}
