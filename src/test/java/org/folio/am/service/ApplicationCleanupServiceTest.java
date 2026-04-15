package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.exception.ApplicationInstalledException;
import org.folio.am.integration.mte.EntitlementService;
import org.folio.am.repository.ApplicationRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationCleanupServiceTest {

  @InjectMocks private ApplicationCleanupService service;
  @Mock private ApplicationRepository applicationRepository;
  @Mock private ApplicationService applicationService;
  @Mock private EntitlementService entitlementService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "entitlementService", entitlementService);
  }

  @Test
  void cleanup_positive_collectsCleanedSkippedAndFailedIds() {
    when(applicationRepository.findAllApplicationIds()).thenReturn(List.of(
      "app-a-1.0.0", "app-b-1.0.0", "app-c-1.0.0"
    ));
    doAnswer(invocation -> {
      var id = invocation.getArgument(0, String.class);
      if ("app-b-1.0.0".equals(id)) {
        throw new ApplicationInstalledException("Application is installed for tenants: [tenant-a]");
      }
      if ("app-c-1.0.0".equals(id)) {
        throw new IllegalStateException("cleanup failed");
      }
      return null;
    }).when(applicationService).delete(anyString(), eq("token"));

    var result = service.cleanup("token");

    assertThat(result.getInspected()).isEqualTo(3);
    assertThat(result.getCleaned()).isEqualTo(1);
    assertThat(result.getSkipped()).isEqualTo(1);
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(result.getCleanedIds()).containsExactly("app-a-1.0.0");
    assertThat(result.getSkippedIds()).containsExactly("app-b-1.0.0");
    assertThat(result.getFailedIds()).containsExactly("app-c-1.0.0");

    verify(applicationService).delete("app-a-1.0.0", "token");
    verify(applicationService).delete("app-b-1.0.0", "token");
    verify(applicationService).delete("app-c-1.0.0", "token");
  }

  @Test
  void cleanup_negative_entitlementServiceIsNotAvailable() {
    ReflectionTestUtils.setField(service, "entitlementService", null);

    assertThatThrownBy(() -> service.cleanup("token"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Applications cleanup is not supported: entitlement service is not available");

    verifyNoInteractions(applicationRepository, applicationService);
  }
}
