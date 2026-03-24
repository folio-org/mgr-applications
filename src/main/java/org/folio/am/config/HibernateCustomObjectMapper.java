package org.folio.am.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

public class HibernateCustomObjectMapper implements ObjectMapperSupplier {

  // NOTE: Jackson 2 (com.fasterxml.jackson) is used here intentionally.
  // ObjectMapperSupplier from hypersistence-utils-hibernate-70:3.15.2 declares get() returning
  // com.fasterxml.jackson.databind.ObjectMapper. Implementing it with tools.jackson.databind.ObjectMapper
  // (Jackson 3) is not possible until hypersistence-utils adds native Jackson 3 support.
  @Override
  public ObjectMapper get() {
    var objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    objectMapper.findAndRegisterModules();
    return objectMapper;
  }
}
