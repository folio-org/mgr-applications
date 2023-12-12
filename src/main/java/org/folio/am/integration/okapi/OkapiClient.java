package org.folio.am.integration.okapi;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import java.util.List;
import org.folio.am.domain.dto.DeploymentDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

public interface OkapiClient {

  @PostMapping("/_/proxy/import/modules")
  void createModuleDescriptors(
    @RequestBody List<ModuleDescriptor> moduleDescriptors,
    @RequestParam("check") boolean check,
    @RequestHeader(TOKEN) String token);

  @DeleteMapping("/_/proxy/modules/{moduleName}")
  void deleteModuleDescriptor(
    @PathVariable("moduleName") String moduleName,
    @RequestHeader(TOKEN) String token);

  @PostMapping("/_/discovery/modules")
  void createDiscovery(
    @RequestBody DeploymentDescriptor descriptor,
    @RequestHeader(TOKEN) String token);

  @DeleteMapping("/_/discovery/modules/{serviceId}/{instanceId}")
  void deleteDiscovery(
    @PathVariable(name = "serviceId") String serviceId,
    @PathVariable(name = "instanceId") String instanceId,
    @RequestHeader(TOKEN) String token);

  @GetMapping("/_/discovery/modules/{srvId}/{instanceId}")
  DeploymentDescriptor getDiscovery(
    @PathVariable("srvId") String srvId,
    @PathVariable("instanceId") String instanceId,
    @RequestHeader(TOKEN) String token);
}
