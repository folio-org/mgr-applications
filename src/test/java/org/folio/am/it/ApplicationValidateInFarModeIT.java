package org.folio.am.it;

import static org.folio.test.TestUtils.parse;
import static org.folio.test.TestUtils.readString;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.ApplicationDescriptorsValidation;
import org.folio.am.support.extensions.EnablePostgres;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.extensions.EnableWireMock;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@Log4j2
@EnableKafka
@EnableWireMock
@EnablePostgres
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:/sql/application-descriptor.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.far-mode.enabled=true",
  "application.kong.enabled=false"
})
class ApplicationValidateInFarModeIT  extends BaseBackendIntegrationTest {

  private static final String APP_PLATFORM_MINIMAL =
    readString("json/application-descriptor/app-platform-minimal.json");
  private static final String APP_PLATFORM_COMPLETE =
    readString("json/application-descriptor/app-platform-complete.json");

  @Test
  void validateDescriptors_positive() throws Exception {
    var descriptor1 = parse(APP_PLATFORM_MINIMAL, ApplicationDescriptor.class);
    var descriptor2 = parse(APP_PLATFORM_COMPLETE, ApplicationDescriptor.class);

    var req = new ApplicationDescriptorsValidation(List.of(descriptor1, descriptor2));

    attemptPost("/applications/validate-descriptors", req)
      .andDo(logResponseBody())
      .andExpect(status().isOk());
  }
}
