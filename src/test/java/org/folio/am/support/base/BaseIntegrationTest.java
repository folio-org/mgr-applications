package org.folio.am.support.base;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.extern.log4j.Log4j2;
import org.assertj.core.util.Arrays;
import org.folio.am.exception.RequestValidationException;
import org.folio.am.support.extensions.EnableKongGateway;
import org.folio.am.support.extensions.EnablePostgres;
import org.folio.common.service.TransactionHelper;
import org.folio.test.FakeKafkaConsumer;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.extensions.EnableWireMock;
import org.folio.test.extensions.impl.KafkaTestExecutionListener;
import org.folio.test.extensions.impl.WireMockExecutionListener;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.ResultMatcher;


/**
 * Base integration test class with required extension and helper methods.
 *
 * <p>Order if extensions is important</p>
 *
 * @see org.junit.jupiter.api.extension.ExtendWith
 */
@Log4j2
@EnableKafka
@EnableWireMock
@EnablePostgres
@SpringBootTest
@EnableKongGateway
@ActiveProfiles("it")
@AutoConfigureMockMvc
@Import({FakeKafkaConsumer.class, TransactionHelper.class})
@TestExecutionListeners(
  value = {WireMockExecutionListener.class, KafkaTestExecutionListener.class},
  mergeMode = MERGE_WITH_DEFAULTS)
public abstract class BaseIntegrationTest extends BaseBackendIntegrationTest {

  protected static FakeKafkaConsumer fakeKafkaConsumer;

  @BeforeAll
  static void setUp(@Autowired FakeKafkaConsumer fakeKafkaConsumer) {
    BaseIntegrationTest.fakeKafkaConsumer = fakeKafkaConsumer;
  }

  protected static ResultMatcher[] requestValidationErr(String errMsg, String fieldName, Object fieldValue) {
    return validationErr(RequestValidationException.class.getSimpleName(), errMsg, fieldName, fieldValue);
  }

  protected static ResultMatcher[] requestValidationErr(String errMsg) {
    return Arrays.array(
      status().isBadRequest(),
      jsonPath("$.errors[0].message", containsString(errMsg)),
      jsonPath("$.errors[0].code", is("validation_error")),
      jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())),
      jsonPath("$.total_records", is(1)));
  }
}
