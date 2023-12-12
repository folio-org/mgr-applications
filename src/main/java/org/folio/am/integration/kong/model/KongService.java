package org.folio.am.integration.kong.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@SuppressWarnings("unused")
public class KongService {

  private String id;
  private String name;
  private List<String> tags;
  private String protocol;
  private String host;
  private String url;
  private String path;
  private Integer retries;
  private Integer port = 80;
  private Boolean enabled = true;

  @JsonProperty("connect_timeout")
  private Integer connectTimeout = 60000;

  @JsonProperty("write_timeout")
  private Integer writeTimeout = 60000;

  @JsonProperty("read_timeout")
  private Integer readTimeout = 60000;

  @JsonProperty("client_certificate")
  private KongEntityIdentifier clientCertificate;

  @JsonProperty("tls_verify")
  private Boolean tlsVerify;

  @JsonProperty("tls_verify_depth")
  private Integer tlsVerifyDepth;

  @JsonProperty("created_at")
  private Long createdAt;

  @JsonProperty("updated_at")
  private Long updatedAt;

  public KongService id(String id) {
    this.id = id;
    return this;
  }

  public KongService name(String name) {
    this.name = name;
    return this;
  }

  public KongService tags(List<String> tags) {
    this.tags = tags;
    return this;
  }

  public KongService protocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  public KongService host(String host) {
    this.host = host;
    return this;
  }

  public KongService url(String url) {
    this.url = url;
    return this;
  }

  public KongService path(String path) {
    this.path = path;
    return this;
  }

  public KongService retries(Integer retries) {
    this.retries = retries;
    return this;
  }

  public KongService port(Integer port) {
    this.port = port;
    return this;
  }

  public KongService connectTimeout(Integer connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  public KongService writeTimeout(Integer writeTimeout) {
    this.writeTimeout = writeTimeout;
    return this;
  }

  public KongService readTimeout(Integer readTimeout) {
    this.readTimeout = readTimeout;
    return this;
  }

  public KongService clientCertificate(KongEntityIdentifier clientCertificate) {
    this.clientCertificate = clientCertificate;
    return this;
  }

  public KongService tlsVerify(Boolean tlsVerify) {
    this.tlsVerify = tlsVerify;
    return this;
  }

  public KongService tlsVerifyDepth(Integer tlsVerifyDepth) {
    this.tlsVerifyDepth = tlsVerifyDepth;
    return this;
  }

  public KongService createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public KongService updatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public KongService enabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }
}
