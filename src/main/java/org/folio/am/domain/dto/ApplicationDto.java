package org.folio.am.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.folio.common.domain.model.ModuleDescriptor;

@Data
public class ApplicationDto {
  private String id;
  private String name;
  private String version;
  private List<ModuleDescriptor> moduleDescriptors = new ArrayList<>();
  private List<Dependency> dependencies = new ArrayList<>();
}
