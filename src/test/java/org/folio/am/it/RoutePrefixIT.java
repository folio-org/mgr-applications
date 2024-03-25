package org.folio.am.it;

import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@Sql(scripts = "classpath:/sql/application-descriptor.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.validation.default-mode=basic",
  "application.router.path-prefix=mgr-applications"
})
class RoutePrefixIT extends BaseIntegrationTest {

  @Test
  void getById_positive() throws Exception {
    doGet("/mgr-applications/applications/{id}", APPLICATION_ID)
      .andExpect(jsonPath("$.id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.name", is("test-app")))
      .andExpect(jsonPath("$.version", is("1.0.0")));
  }
}
