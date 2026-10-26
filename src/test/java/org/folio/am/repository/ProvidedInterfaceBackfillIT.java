package org.folio.am.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.am.support.base.BaseRepositoryTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.StreamUtils;

/**
 * Checks the one-time backfill of {@code module_interface_reference.provided_interface} that runs in the
 * {@code add-provided-interface-to-module-interface-reference} changeset, against rows as they exist before it.
 */
@IntegrationTest
@Sql(scripts = "classpath:/sql/module-interface-references-without-provided-interface.sql",
  executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class ProvidedInterfaceBackfillIT extends BaseRepositoryTest {

  private static final String BACKFILL_SQL =
    "changelog/changes/v4.1.0/populate-module-interface-reference-provided-interface.sql";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void backfill_positive_providesRowsGetMatchingInterfaceFromDescriptor() throws IOException {
    runBackfill();

    assertThat(providedInterfaceOf("mod-a-1.0.0", "api-a", "PROVIDES"))
      .contains("\"id\": \"api-a\"", "\"interfaceType\": \"multiple\"", "\"pathPattern\": \"/a\"")
      .doesNotContain("api-b");
    assertThat(providedInterfaceOf("mod-a-1.0.0", "api-b", "PROVIDES")).contains("\"id\": \"api-b\"");
  }

  @Test
  void backfill_positive_rowsWithoutMatchingElementStayNull() throws IOException {
    runBackfill();

    assertThat(providedInterfaceOf("mod-a-1.0.0", "api-without-element", "PROVIDES")).isNull();
    assertThat(providedInterfaceOf("mod-no-provides-1.0.0", "api-c", "PROVIDES")).isNull();
    assertThat(providedInterfaceOf("mod-invalid-provides-1.0.0", "api-x", "PROVIDES")).isNull();
  }

  @Test
  void backfill_positive_requiresAndOptionalRowsStayNull() throws IOException {
    runBackfill();

    assertThat(providedInterfaceOf("mod-a-1.0.0", "api-required", "REQUIRES")).isNull();
    assertThat(providedInterfaceOf("mod-a-1.0.0", "api-optional", "OPTIONAL")).isNull();
  }

  private void runBackfill() throws IOException {
    var sql = StreamUtils.copyToString(new ClassPathResource(BACKFILL_SQL).getInputStream(), StandardCharsets.UTF_8);
    jdbcTemplate.execute(sql);
  }

  private String providedInterfaceOf(String moduleId, String interfaceId, String type) {
    return jdbcTemplate.queryForObject("SELECT provided_interface::text FROM module_interface_reference "
      + "WHERE module_id = ? AND id = ? AND type = ?::interface_ref_type", String.class, moduleId, interfaceId, type);
  }
}
