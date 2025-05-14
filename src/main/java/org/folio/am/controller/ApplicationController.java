package org.folio.am.controller;

import static java.lang.Boolean.TRUE;
import static org.springframework.http.HttpStatus.CREATED;

import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDescriptors;
import org.folio.am.domain.dto.ApplicationReferences;
import org.folio.am.domain.dto.ValidationMode;
import org.folio.am.domain.model.ValidationContext;
import org.folio.am.rest.resource.ApplicationsApi;
import org.folio.am.service.ApplicationReferencesValidationService;
import org.folio.am.service.ApplicationService;
import org.folio.am.service.ApplicationValidatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController extends BaseController implements ApplicationsApi {

  private final ApplicationValidatorService applicationValidatorService;
  private final ApplicationReferencesValidationService applicationReferencesValidationService;
  private final ApplicationService applicationService;

  @Override
  public ResponseEntity<ApplicationDescriptor> getApplicationById(String id, Boolean includeModuleDescriptors) {
    return ResponseEntity.ok(applicationService.get(id, includeModuleDescriptors));
  }

  @Override
  public ResponseEntity<ApplicationDescriptors> getApplicationsByQuery(String query, Integer offset, Integer limit,
    Boolean includeModuleDescriptors) {
    var result = applicationService.findByQuery(query, offset, limit, includeModuleDescriptors);
    return ResponseEntity.ok(new ApplicationDescriptors()
      .totalRecords(result.getTotalRecords())
      .applicationDescriptors(result.getRecords()));
  }

  @Override
  public ResponseEntity<ApplicationDescriptor> registerApplication(String token,
    Boolean check, ApplicationDescriptor descriptor) {
    var createdDescriptor = applicationService.create(descriptor, token, TRUE.equals(check));
    return ResponseEntity.status(CREATED).body(createdDescriptor);
  }

  @Override
  public ResponseEntity<Void> deregisterApplicationById(String id, String token) {
    applicationService.delete(id, token);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> validateApplicationDescriptor(ValidationMode mode,
    ApplicationDescriptor applicationDescriptor) {

    var validationContext = ValidationContext.builder().applicationDescriptor(applicationDescriptor).build();
    if (ValidationMode.DEFAULT.equals(mode)) {
      applicationValidatorService.validate(validationContext);
    } else {
      applicationValidatorService.validate(validationContext, toServiceMode(mode));
    }

    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> validateModulesInterfaceIntegrity(ApplicationReferences applicationReferences) {
    applicationReferencesValidationService.validate(applicationReferences);
    return ResponseEntity.noContent().build();
  }

  private static org.folio.am.service.validator.ValidationMode toServiceMode(ValidationMode mode) {
    return org.folio.am.service.validator.ValidationMode.fromValue(mode.getValue());
  }
}
