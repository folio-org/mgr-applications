package org.folio.am.service;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.folio.am.config.properties.HttpClientProperties;
import org.folio.am.exception.ServiceException;
import org.folio.am.support.TestValues;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleDescriptorLoaderTest {

  @InjectMocks private ModuleDescriptorLoader moduleDescriptorLoader;
  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;
  @Mock private HttpClientProperties httpClientProperties;
  @Spy private ObjectMapper objectMapper;

  @Test
  void loadByUrls_positive() throws Exception {
    var url = "http://testhost.test/modules/foo-module-1.0.0";
    var name = "foo-module";
    var version = "1.0.0";
    var module = TestValues.module(name, version, url);
    var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
    var body = "{\"name\":\"foo-module\",\"id\":\"foo-module-1.0.0\"}";

    when(httpClient.send(request, ofString())).thenReturn(httpResponse);
    when(httpResponse.body()).thenReturn(body);
    when(httpClientProperties.getReadTimeout()).thenReturn(1000L);

    var moduleDescriptors = moduleDescriptorLoader.loadByUrls(List.of(module));

    var expectedModuleDescriptor = new ModuleDescriptor()
      .description(name).id(name + "-" + version);
    assertNotNull(moduleDescriptors);
    assertThat(moduleDescriptors.size()).isEqualTo(1);
    assertThat(moduleDescriptors.get(0)).isEqualTo(expectedModuleDescriptor);
    verify(objectMapper).readValue(body, ModuleDescriptor.class);
    verify(httpClientProperties).getReadTimeout();
  }

  @Test
  void loadByUrls_negative_emptyUrl() {
    var module = TestValues.module("foo-module", "1.0.0", null);

    var moduleDescriptors = moduleDescriptorLoader.loadByUrls(List.of(module));

    assertThat(moduleDescriptors).isEmpty();
  }

  @Test
  void loadByUrls_negative_emptyModules() {
    var moduleDescriptors = moduleDescriptorLoader.loadByUrls(List.of());

    assertThat(moduleDescriptors).isEmpty();
  }

  @Test
  void loadByUrls_negative_failedToLoad() throws Exception {
    var url = "http://testhost.test/modules/foo-module-1.0.0";
    var module = TestValues.module("foo-module", "1.0.0", url);
    var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();

    when(httpClient.send(request, ofString())).thenThrow(new InterruptedException());
    when(httpClientProperties.getReadTimeout()).thenReturn(1000L);

    var modules = List.of(module);
    assertThatThrownBy(() -> moduleDescriptorLoader.loadByUrls(modules))
      .hasMessage("Failed to load module descriptor by url: " + url)
      .isInstanceOf(ServiceException.class);
    verify(httpClientProperties).getReadTimeout();
  }
}
