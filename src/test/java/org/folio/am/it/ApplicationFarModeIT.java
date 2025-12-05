package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.folio.am.support.TestValues.getApplicationDescriptor;
import static org.folio.test.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Dependency;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@Sql(scripts = "classpath:/sql/application-descriptor.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {"application.far-mode.enabled=true", "application.kong.enabled=false"})
class ApplicationFarModeIT extends BaseIntegrationTest {

  @Test
  void create_positive() throws Exception {
    var applicationDescriptor = getApplicationDescriptor("test-module-1.1.0", "1.1.0");

    doPost("/applications", applicationDescriptor)
      .andExpect(jsonPath("$.id", is("test-0.1.1")))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")));
  }

  @Test
  void delete_positive() throws Exception {
    doDelete("/applications/{id}", APPLICATION_ID);
    doGet(get("/applications").queryParam("query", "cql.allRecords=1"))
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }

  @Test
  void create_positive_with_dependency() throws Exception {

    var applicationDescriptor = new ApplicationDescriptor()
      .name("test")
      .version("0.1.1");

    var mvcResult = doPost("/applications", applicationDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.name", is("test")))
      .andExpect(jsonPath("$.version", is("0.1.1")))
      .andReturn();

    var applicationDescriptor1 = parseResponse(mvcResult, ApplicationDescriptor.class);

    var dependency = new Dependency()
      .name(applicationDescriptor1.getName()).version(applicationDescriptor1.getVersion());

    var applicationDescriptor2 = new ApplicationDescriptor()
      .name("test2")
      .version("0.1.2")
      .dependencies(List.of(dependency));

    var mvcResult2 = doPost("/applications", applicationDescriptor2)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.name", is("test2")))
      .andExpect(jsonPath("$.version", is("0.1.2")))
      .andReturn();

    var result2 = parseResponse(mvcResult2, ApplicationDescriptor.class);
    assertThat(result2.getDependencies()).hasSize(1);
    assertThat(result2.getDependencies().get(0)).isEqualTo(dependency);
  }
}
