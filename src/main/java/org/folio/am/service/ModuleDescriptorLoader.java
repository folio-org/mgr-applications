package org.folio.am.service;

import static java.lang.Thread.currentThread;
import static java.net.URI.create;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.time.Duration.ofMillis;
import static java.util.stream.Collectors.toList;
import static org.folio.common.utils.CollectionUtils.toStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.config.properties.HttpClientProperties;
import org.folio.am.domain.dto.Module;
import org.folio.am.exception.ServiceException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class ModuleDescriptorLoader {

  private final HttpClient client;
  private final ObjectMapper objectMapper;
  private final HttpClientProperties httpClientProperties;

  public List<ModuleDescriptor> loadByUrls(List<Module> modules) {
    return toStream(modules)
      .map(Module::getUrl)
      .map(Optional::ofNullable)
      .filter(Optional::isPresent)
      .flatMap(Optional::stream)
      .map(this::loadByUrl)
      .collect(toList());
  }

  private ModuleDescriptor loadByUrl(String url) {
    var request = buildRequest(url);
    try {
      HttpResponse<String> response = client.send(request, ofString());
      var body = response.body();
      return objectMapper.readValue(body, ModuleDescriptor.class);
    } catch (IOException e) {
      throw buildError(url, e);
    } catch (InterruptedException e) {
      currentThread().interrupt();
      throw buildError(url, e);
    }
  }

  private HttpRequest buildRequest(String url) {
    return HttpRequest.newBuilder()
      .GET()
      .uri(create(url))
      .timeout(ofMillis(httpClientProperties.getReadTimeout()))
      .build();
  }

  private static ServiceException buildError(String url, Exception e) {
    return new ServiceException("Failed to load module descriptor by url: " + url, e);
  }
}
