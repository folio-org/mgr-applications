package org.folio.am.service.validator;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.core.annotation.Order;

@Order(ValidatorOrder.UI_MODULE_DESCRIPTOR_VALIDATOR)
@BasicValidator
public class UiModuleDescriptorValidator extends AbstractModuleDescriptorValidator {

  /**
   * Initializes {@link UiModuleDescriptorValidator} component.
   */
  public UiModuleDescriptorValidator() {
    super("UI Module descriptors", "uiModuleDescriptors");
  }

  @Override
  protected List<Module> getModules(ApplicationDescriptor descriptor) {
    return descriptor.getUiModules();
  }

  @Override
  protected List<ModuleDescriptor> getModuleDescriptors(ValidationContext context) {
    return context.getAllUiModuleDescriptors();
  }
}
