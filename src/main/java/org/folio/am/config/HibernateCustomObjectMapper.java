package org.folio.am.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

public class HibernateCustomObjectMapper implements ObjectMapperSupplier {

  @Override
  public ObjectMapper get() {
    var objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
