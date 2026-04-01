package org.folio.am.config;

import io.hypersistence.utils.hibernate.type.util.JsonSerializer;
import tools.jackson.databind.ObjectMapper;

public class HibernateCustomJsonSerializer implements JsonSerializer {

  private static final ObjectMapper OBJECT_MAPPER = new HibernateCustomObjectMapper().get();

  @Override
  @SuppressWarnings("unchecked")
  public <T> T clone(T value) {
    byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(value);
    return (T) OBJECT_MAPPER.readValue(bytes, value.getClass());
  }
}
