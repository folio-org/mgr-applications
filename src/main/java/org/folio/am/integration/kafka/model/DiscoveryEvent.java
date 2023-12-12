package org.folio.am.integration.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiscoveryEvent  {
  private String moduleId;
}
