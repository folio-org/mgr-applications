package org.folio.am.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.domain.entity.ModuleEntity;
import org.folio.am.mapper.ModuleDiscoveryMapper;
import org.folio.am.repository.ModuleRepository;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDiscoveryServiceTest {

  @InjectMocks private ApplicationDiscoveryService service;
  @Mock private ModuleRepository repository;
  @Mock private ModuleDiscoveryMapper mapper;

  @Test
  void get_positive() {
    var entities = singletonList(TestValues.moduleEntity());
    var expectedPage = new PageImpl<>(entities);
    when(repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);

    when(mapper.convert(TestValues.moduleEntity())).thenReturn(TestValues.moduleDiscovery());

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.moduleDiscoveries(TestValues.moduleDiscovery()));
  }

  @Test
  void get_positive_emptyResult() {
    var expectedPage = new PageImpl<ModuleEntity>(emptyList());
    when(repository.findAllByHasDiscoveryAndApplicationIdsIn(List.of(APPLICATION_ID), OffsetRequest.of(0, 10)))
      .thenReturn(expectedPage);

    var actual = service.get(APPLICATION_ID, 0, 10);

    assertThat(actual).isEqualTo(TestValues.emptyModuleDiscoveries());
  }
}
