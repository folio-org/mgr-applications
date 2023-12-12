package org.folio.am.controller;

import org.folio.am.controller.converter.ValidationModeConverters;
import org.folio.security.configuration.SecurityConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@SpringBootConfiguration
@Import({
  ApiExceptionHandler.class,
  SecurityConfiguration.class,
  ValidationModeConverters.FromString.class,
  ValidationModeConverters.ToString.class
})
public class ControllerTestConfiguration {}
