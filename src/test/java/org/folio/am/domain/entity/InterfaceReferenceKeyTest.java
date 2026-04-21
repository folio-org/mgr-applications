package org.folio.am.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.domain.entity.InterfaceReferenceEntity.ReferenceType;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class InterfaceReferenceKeyTest {

  @Test
  void equals_sameModuleAndInterfaceDifferentType_notEqual() {
    var provides = InterfaceReferenceKey.of("mod-configuration-1.0.0", "configuration", ReferenceType.PROVIDES);
    var requires = InterfaceReferenceKey.of("mod-configuration-1.0.0", "configuration", ReferenceType.REQUIRES);

    assertThat(provides).isNotEqualTo(requires);
  }
}
