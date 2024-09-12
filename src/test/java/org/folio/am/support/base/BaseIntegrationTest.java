package org.folio.am.support.base;

import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

import lombok.extern.log4j.Log4j2;
import org.folio.am.support.TestUtils;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;


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
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Import({FakeKafkaConsumer.class, TransactionHelper.class})
@TestExecutionListeners(
  value = {WireMockExecutionListener.class, KafkaTestExecutionListener.class},
  mergeMode = MERGE_WITH_DEFAULTS)
public abstract class BaseIntegrationTest extends BaseBackendIntegrationTest {

  protected static FakeKafkaConsumer fakeKafkaConsumer;

  static {
    TestUtils.disableSslVerification();
  }

  @BeforeAll
  static void setUp(@Autowired FakeKafkaConsumer fakeKafkaConsumer) {
    BaseIntegrationTest.fakeKafkaConsumer = fakeKafkaConsumer;
  }
}
