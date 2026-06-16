package org.folio.am.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discovery change event published to {@code {ENV}.discovery} and consumed by the bootstrap-cache
 * invalidation listener. The no-arg constructor is required so the consumer's
 * {@code JacksonJsonDeserializer} can instantiate it via the no-arg-constructor + setter path (the
 * all-args constructor alone is not deserialization-friendly); {@code @JsonProperty} pins the JSON
 * field name, mirroring {@code TenantEntitlementEvent}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryEvent {

  @JsonProperty("moduleId")
  private String moduleId;
}
