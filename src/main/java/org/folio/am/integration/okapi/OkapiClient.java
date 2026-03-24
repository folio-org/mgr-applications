package org.folio.am.integration.okapi;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import java.util.List;
import org.folio.am.domain.dto.DeploymentDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface OkapiClient {

  @PostExchange("/_/proxy/import/modules")
  void createModuleDescriptors(
    @RequestBody List<ModuleDescriptor> moduleDescriptors,
    @RequestParam("check") boolean check,
    @RequestHeader(TOKEN) String token);

  @DeleteExchange("/_/proxy/modules/{moduleName}")
  void deleteModuleDescriptor(
    @PathVariable("moduleName") String moduleName,
    @RequestHeader(TOKEN) String token);

  @PostExchange("/_/discovery/modules")
  void createDiscovery(
    @RequestBody DeploymentDescriptor descriptor,
    @RequestHeader(TOKEN) String token);

  @DeleteExchange("/_/discovery/modules/{serviceId}/{instanceId}")
  void deleteDiscovery(
    @PathVariable(name = "serviceId") String serviceId,
    @PathVariable(name = "instanceId") String instanceId,
    @RequestHeader(TOKEN) String token);

  @GetExchange("/_/discovery/modules/{srvId}/{instanceId}")
  DeploymentDescriptor getDiscovery(
    @PathVariable("srvId") String srvId,
    @PathVariable("instanceId") String instanceId,
    @RequestHeader(TOKEN) String token);
}
