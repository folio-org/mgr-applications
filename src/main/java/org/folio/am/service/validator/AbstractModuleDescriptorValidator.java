package org.folio.am.service.validator;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.ListUtils.subtract;
import static org.folio.am.utils.CollectionUtils.toStream;

import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.exception.RequestValidationException;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;

@RequiredArgsConstructor
public abstract class AbstractModuleDescriptorValidator implements ApplicationValidator {

  private final String name;
  private final String fieldName;

  @Override
  public void validate(ValidationContext context) {
    var moduleIds = getIds(getModules(context.getApplicationDescriptor()), Module::getArtifactId);
    var moduleDescriptorIds = getIds(getModuleDescriptors(context), ModuleDescriptor::getId);

    var undefinedModules = subtract(moduleIds, moduleDescriptorIds);
    var redundantModuleDescriptorIds = subtract(moduleDescriptorIds, moduleIds);

    if (isEmpty(moduleIds) && isEmpty(moduleDescriptorIds)) {
      return;
    }

    var descriptor = context.getApplicationDescriptor();
    if (isNotEmpty(undefinedModules)) {
      throw new RequestValidationException(format(
        "%s are not found in application descriptor: %s", this.name, descriptor.getArtifactId()),
        this.fieldName, toStringRepresentation(undefinedModules));
    }

    if (isNotEmpty(redundantModuleDescriptorIds)) {
      throw new RequestValidationException(format(
        "%s are not used in application descriptor: %s", this.name, descriptor.getArtifactId()),
        this.fieldName, toStringRepresentation(redundantModuleDescriptorIds));
    }
  }

  /**
   * Provides module definitions, extracted from {@link ApplicationDescriptor} object.
   *
   * @param descriptor - application descriptor
   * @return {@link List} with {@link Module} objects, can be null
   */
  protected abstract List<Module> getModules(ApplicationDescriptor descriptor);

  /**
   * Provides module descriptors, extracted from {@link ValidationContext} object.
   *
   * @param context - validation context
   * @return {@link List} with {@link ModuleDescriptor} objects, can be null
   */
  protected abstract List<ModuleDescriptor> getModuleDescriptors(ValidationContext context);

  private static <T> List<String> getIds(List<T> list, Function<T, String> idExtractor) {
    return toStream(list).map(idExtractor).distinct().collect(toList());
  }

  private static String toStringRepresentation(List<String> undefinedModules) {
    return undefinedModules.stream().collect(joining(", ", "[", "]"));
  }
}
