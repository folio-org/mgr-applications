package org.folio.am.integration.kong;

import org.folio.am.integration.kong.model.KongService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface KongAdminClient {

  /**
   * Retrieves {@link KongService} object by its id.
   *
   * @param serviceName - kong service name
   * @return retrieved {@link KongService} object
   */
  @GetMapping("/services/{id}")
  KongService getService(@PathVariable("id") String serviceName);

  /**
   * Updates a service in Kong gateway.
   *
   * @param serviceId - service name or id
   * @param service - service descriptor
   * @return created {@link KongService} object
   */
  @PutMapping("/services/{serviceId}")
  KongService upsertService(@PathVariable("serviceId") String serviceId, @RequestBody KongService service);

  /**
   * Deletes a service in Kong gateway by its name or id.
   *
   * @param serviceId - service name or id
   */
  @DeleteMapping("/services/{serviceId}")
  void deleteService(@PathVariable("serviceId") String serviceId);
}
