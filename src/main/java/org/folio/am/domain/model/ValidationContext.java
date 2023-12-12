package org.folio.am.domain.model;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.service.validator.ValidationMode;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;

@EqualsAndHashCode
@ToString
@Builder
public class ValidationContext {

  @Getter
  private final ApplicationDescriptor applicationDescriptor;
  private List<ModuleDescriptor> loadedModuleDescriptors;
  private List<ModuleDescriptor> loadedUiModuleDescriptors;
  private List<ModuleDescriptor> allModuleDescriptors;
  private List<ModuleDescriptor> allUiModuleDescriptors;
  @Getter
  private List<ValidationMode> additionalModes;

  public List<ModuleDescriptor> getAllModuleDescriptors() {
    if (isNull(allModuleDescriptors)) {
      var moduleDescriptors = new HashSet<>(emptyIfNull(loadedModuleDescriptors));
      moduleDescriptors.addAll(emptyIfNull(applicationDescriptor.getModuleDescriptors()));
      allModuleDescriptors = new ArrayList<>(moduleDescriptors);
    }

    return allModuleDescriptors;
  }

  public List<ModuleDescriptor> getAllUiModuleDescriptors() {
    if (isNull(allUiModuleDescriptors)) {
      var moduleDescriptors = new HashSet<>(emptyIfNull(loadedUiModuleDescriptors));
      moduleDescriptors.addAll(emptyIfNull(applicationDescriptor.getUiModuleDescriptors()));
      allUiModuleDescriptors = new ArrayList<>(moduleDescriptors);
    }

    return allUiModuleDescriptors;
  }
}
