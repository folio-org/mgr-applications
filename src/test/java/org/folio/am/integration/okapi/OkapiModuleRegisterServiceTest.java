package org.folio.am.integration.okapi;

import static java.util.Collections.emptyList;
import static org.folio.am.support.TestConstants.MODULE_ID;
import static org.folio.am.support.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.am.support.TestConstants.SERVICE_ID;
import static org.folio.am.support.TestConstants.SERVICE_NAME;
import static org.folio.am.support.TestConstants.SERVICE_VERSION;
import static org.folio.am.support.TestConstants.UPDATED_URL;
import static org.folio.am.support.TestValues.applicationDescriptor;
import static org.folio.am.support.TestValues.applicationDescriptorEntity;
import static org.folio.am.support.TestValues.deploymentDescriptor;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.util.ArrayList;
import org.folio.am.domain.entity.ArtifactEntity;
import org.folio.am.repository.ModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModuleRegisterServiceTest {

  @Mock private OkapiClient okapiClient;
  @Mock private ModuleRepository moduleRepository;
  @InjectMocks private OkapiModuleRegisterService service;

  @Test
  void onDescriptorCreate_positive() {
    var applicationDescriptor = applicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    doNothing().when(okapiClient).createModuleDescriptors(moduleDescriptors, false, OKAPI_AUTH_TOKEN);
    service.onDescriptorCreate(applicationDescriptor, OKAPI_AUTH_TOKEN);
    verify(okapiClient).createModuleDescriptors(moduleDescriptors, false, OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDescriptorCreate_negative() {
    var applicationDescriptor = applicationDescriptor();
    applicationDescriptor.setModuleDescriptors(null);
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();

    service.onDescriptorCreate(applicationDescriptor, OKAPI_AUTH_TOKEN);
    verify(okapiClient, never()).createModuleDescriptors(moduleDescriptors, false, OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDescriptorDelete_positive_modulesDontExist() {
    var appDescriptor = applicationDescriptor();

    var modules = new ArrayList<>(applicationDescriptorEntity(appDescriptor).getBackendModules());
    var moduleIds = mapItems(modules, ArtifactEntity::getId);
    when(moduleRepository.findAllById(moduleIds)).thenReturn(emptyList());

    var moduleDescriptors = appDescriptor.getModuleDescriptors();
    moduleDescriptors.forEach(md ->
      doNothing().when(okapiClient).deleteModuleDescriptor(md.getId(), OKAPI_AUTH_TOKEN));

    service.onDescriptorDelete(appDescriptor, OKAPI_AUTH_TOKEN);

    verify(okapiClient).deleteModuleDescriptor(MODULE_ID,  OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDescriptorDelete_negative_emptyModuleDescriptors() {
    var applicationDescriptor = applicationDescriptor();
    applicationDescriptor.setModuleDescriptors(null);

    service.onDescriptorDelete(applicationDescriptor, OKAPI_AUTH_TOKEN);
    verify(okapiClient, times(0)).deleteModuleDescriptor(MODULE_ID,  OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDescriptorDelete_negative_modulesExist() {
    var appDescriptor = applicationDescriptor();

    var modules = new ArrayList<>(applicationDescriptorEntity(appDescriptor).getBackendModules());
    var moduleIds = mapItems(modules, ArtifactEntity::getId);
    when(moduleRepository.findAllById(moduleIds)).thenReturn(modules);

    service.onDescriptorDelete(appDescriptor, OKAPI_AUTH_TOKEN);

    verify(okapiClient, never()).deleteModuleDescriptor(MODULE_ID,  OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDiscoveryCreate_positive() {
    when(okapiClient.getDiscovery(MODULE_ID, MODULE_ID, OKAPI_AUTH_TOKEN)).thenThrow(FeignException.NotFound.class);

    service.onDiscoveryCreate(moduleDiscovery(), OKAPI_AUTH_TOKEN);

    var deploymentDescriptor = deploymentDescriptor();
    verify(okapiClient).createDiscovery(deploymentDescriptor, OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDiscoveryCreate_negative() {
    var deploymentDescriptor = deploymentDescriptor();
    when(okapiClient.getDiscovery(MODULE_ID, MODULE_ID, OKAPI_AUTH_TOKEN)).thenReturn(deploymentDescriptor);

    service.onDiscoveryCreate(moduleDiscovery(), OKAPI_AUTH_TOKEN);
    verify(okapiClient, never()).createDiscovery(deploymentDescriptor, OKAPI_AUTH_TOKEN);
  }

  @Test
  void onDiscoveryUpdate_positive() {
    var deploymentDescriptor = deploymentDescriptor();

    when(okapiClient.getDiscovery(SERVICE_ID, SERVICE_ID, OKAPI_AUTH_TOKEN)).thenReturn(deploymentDescriptor);

    service.onDiscoveryCreate(moduleDiscovery(SERVICE_NAME, SERVICE_VERSION, UPDATED_URL), OKAPI_AUTH_TOKEN);

    var updatedDescriptor = deploymentDescriptor(SERVICE_ID, UPDATED_URL);
    verify(okapiClient).deleteDiscovery(SERVICE_ID, SERVICE_ID, OKAPI_AUTH_TOKEN);
    verify(okapiClient).createDiscovery(updatedDescriptor, OKAPI_AUTH_TOKEN);
  }
}
