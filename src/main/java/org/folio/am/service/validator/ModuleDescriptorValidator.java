package org.folio.am.service.validator;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.MODULE_DESCRIPTOR_VALIDATOR)
@BasicValidator
public class ModuleDescriptorValidator extends AbstractModuleDescriptorValidator {

  /**
   * Initializes {@link ModuleDescriptorValidator} component.
   */
  public ModuleDescriptorValidator() {
    super("Module descriptors", "moduleDescriptors");
  }

  @Override
  protected List<Module> getModules(ApplicationDescriptor descriptor) {
    return descriptor.getModules();
  }

  @Override
  protected List<ModuleDescriptor> getModuleDescriptors(ValidationContext context) {
    return context.getAllModuleDescriptors();
  }
}
