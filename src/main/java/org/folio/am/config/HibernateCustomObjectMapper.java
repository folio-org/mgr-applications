package org.folio.am.config;

import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;


public class HibernateCustomObjectMapper implements ObjectMapperSupplier {

  @Override
  public ObjectMapper get() {
    return new ObjectMapper()
      .rebuild()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .findAndAddModules()
      .build();
  }
}
