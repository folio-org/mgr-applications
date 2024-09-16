package org.folio.am.support;

import static dasniko.testcontainers.keycloak.ExtendableKeycloakContainer.MASTER_REALM;
import static java.net.URLEncoder.encode;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier;
import static javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory;
import static javax.net.ssl.SSLContext.getInstance;
import static org.folio.am.support.TestConstants.HTTP_CLIENT_DUMMY_SSL;
import static org.folio.test.TestUtils.OBJECT_MAPPER;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;

@UtilityClass
public class TestUtils {

  public static HttpClient httpClientWithDummySslContext() {
    return HttpClient.newBuilder().sslContext(dummySslContext()).build();
  }

  @SneakyThrows({InterruptedException.class, java.io.IOException.class})
  public static String generateAccessToken(KeycloakProperties properties) {
    var admin = properties.getAdmin();
    var tokenRequestBody = Map.of(
      "client_id", admin.getClientId(),
      "client_secret", admin.getPassword(),
      "grant_type", admin.getGrantType());

    var keycloakBaseUrl = StringUtils.removeEnd(properties.getUrl(), "/");
    var uri = URI.create(String.format("%s/realms/%s/protocol/openid-connect/token", keycloakBaseUrl, MASTER_REALM));
    var request = HttpRequest.newBuilder(uri)
      .method(POST.name(), ofString(toFormUrlencodedValue(tokenRequestBody), UTF_8))
      .header("Content-Type", APPLICATION_FORM_URLENCODED_VALUE)
      .build();

    var response = HTTP_CLIENT_DUMMY_SSL.send(request, BodyHandlers.ofString(UTF_8));
    var keycloakTokenJson = OBJECT_MAPPER.readTree(response.body());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Failed to get access token: " + keycloakTokenJson);
    }
    return keycloakTokenJson.path("access_token").asText();
  }

  private static String toFormUrlencodedValue(Map<String, String> params) {
    return params.entrySet()
      .stream()
      .map(entry -> String.format("%s=%s", encode(entry.getKey(), UTF_8), encode(entry.getValue(), UTF_8)))
      .collect(Collectors.joining("&"));
  }

  public static void disableSslVerification() {
    try {
      var sc = dummySslContext();
      setDefaultSSLSocketFactory(sc.getSocketFactory());
      var allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

      setDefaultHostnameVerifier(allHostsValid);
    } catch (Exception e) {
      throw new RuntimeException("Failed to disable SSL verification", e);
    }
  }

  @SneakyThrows
  public static SSLContext dummySslContext() {
    var dummyTrustManager = new X509ExtendedTrustManager() {

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };

    var sslContext = getInstance("TLS");
    sslContext.init(null, new TrustManager[] {dummyTrustManager}, new SecureRandom());
    return sslContext;
  }
}
