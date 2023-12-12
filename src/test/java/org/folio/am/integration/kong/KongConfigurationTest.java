package org.folio.am.integration.kong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.Contract;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class KongConfigurationTest {

  private final KongConfiguration kongConfiguration = new KongConfiguration();

  @Test
  void kongAdminClient_positive() {
    var configuration = mock(KongConfigurationProperties.class);
    when(configuration.getUrl()).thenReturn("http://kong:8001");

    var kongAdminClient = kongConfiguration.kongAdminClient(
      configuration, mock(Contract.class), mock(Encoder.class), mock(Decoder.class));

    assertThat(kongAdminClient).isNotNull();
  }

  @Test
  void kongGatewayService_positive() {
    var kongAdminClient = mock(KongAdminClient.class);
    var gatewayService = kongConfiguration.kongGatewayService(kongAdminClient);
    assertThat(gatewayService).isNotNull();
  }
}
