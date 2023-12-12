package org.folio.am.config;

import static java.time.Duration.ofMillis;

import java.net.http.HttpClient;
import lombok.RequiredArgsConstructor;
import org.folio.am.config.properties.HttpClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfiguration {

  private final HttpClientProperties httpClientProperties;

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(ofMillis(httpClientProperties.getConnectionTimeout())).build();
  }
}
