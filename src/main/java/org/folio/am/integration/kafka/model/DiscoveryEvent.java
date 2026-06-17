package org.folio.am.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discovery change event published to {@code {ENV}.discovery} and consumed by the bootstrap-cache
 * invalidation listener. The no-arg constructor is required so the consumer's
 * {@code JacksonJsonDeserializer} can instantiate it via the no-arg-constructor + setter path (the
 * all-args constructor alone is not deserialization-friendly); {@code @JsonProperty} pins the JSON
 * field name, mirroring {@code TenantEntitlementEvent}.
 *
 * <p>{@code dependentModuleIds} is populated only for delete events: it carries the provider fan-out captured
 * before the module's {@code PROVIDES} rows are removed, so every replica can evict the affected dependents
 * (re-deriving them post-delete would yield nothing). It is null/absent for create and update events, where the
 * consumer re-derives the fan-out from the still-present rows. Other consumers of this topic (e.g. sidecars)
 * ignore the field.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryEvent {

  @JsonProperty("moduleId")
  private String moduleId;

  // Omitted from the wire when null, so create/update events serialize identically to the pre-change format and
  // only delete events carry the field — keeping the rolling-upgrade blast radius to delete events alone.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("dependentModuleIds")
  private List<String> dependentModuleIds;

  public DiscoveryEvent(String moduleId) {
    this.moduleId = moduleId;
  }
}
