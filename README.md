# mgr-applications

Copyright (C) 2022-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Running It](#running-it)
* [Environment Variables](#environment-variables)
  * [SSL Configuration environment variables](#ssl-configuration-environment-variables)
  * [Secure storage environment variables](#secure-storage-environment-variables)
    * [AWS-SSM](#aws-ssm)
    * [Vault](#vault)
    * [Folio Secure Store Proxy (FSSP)](#folio-secure-store-proxy-fssp)
* [Keycloak Integration](#keycloak-integration)
  * [Import Keycloak data on startup](#import-keycloak-data-on-startup)
  * [Keycloak Security](#keycloak-security)
  * [Keycloak specific environment variables](#keycloak-specific-environment-variables)
* [Kong Gateway Integration](#kong-gateway-integration)
  * [Kong Service Registration](#kong-service-registration)
  * [Kong Route Registration](#kong-route-registration)
* [Kafka Integration](#kafka-integration)
  * [Events upon discovery changes](#events-upon-discovery-changes)
  * [Naming convention](#naming-convention)
  * [Event structure](#event-structure)
* [Manager Tenant Entitlements Integration](#manager-tenant-entitlements-integration)
* [Folio Application Registry mode](#folio-application-registry-mode)

## Introduction

`mgr-applications` provides following functionality:

* Dependency check / platform integrity validation
* (De-)Registration of applications
* Enabling/disabling of an application (including dependencies)
* (Un-)Deployment of an application (optional)
* Application health and availability monitoring
* Optional integration with Kong gateway
  * Add/remove services on application discovery update

## Compiling

```shell
mvn clean install
```

See that it says `BUILD SUCCESS` near the end.

If you want to skip tests:

```shell
mvn clean install -DskipTests
```

## Running It

Run locally with proper environment variables set (see [Environment variables](#environment-variables) below) on
listening port 8081 (default listening port):

```shell
java \
  -Dokapi.url=http://localhost:9130 \
  -Dokapi.token=${okapiToken} \
  -jar target/mgr-applications-*.jar
```

Build the docker container with following script after compilation:

```shell
docker build -t mgr-applications .
```

Test that it runs with:

```shell
docker run \
  --name mgr-applications \
  --link postgres:postgres \
  -e DB_HOST=postgres \
  -e okapi.url=http://okapi:9130 \
  -e tenant.url=http://mgr-tenants:8081 \
  -e te.url=http://mgr-tenant-entitlements:8081 \
  -e okapi.token=${okapiToken} \
  -p 8081:8081 \
  -d mgr-applications
```

## Environment Variables

| Name                                     | Default value                | Required | Description                                                                                                                                                                                                |
|:-----------------------------------------|:-----------------------------|:--------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DB_HOST                                  | localhost                    |  false   | Postgres hostname                                                                                                                                                                                          |
| DB_PORT                                  | 5432                         |  false   | Postgres port                                                                                                                                                                                              |
| DB_USERNAME                              | postgres                     |  false   | Postgres username                                                                                                                                                                                          |
| DB_PASSWORD                              | postgres                     |  false   | Postgres username password                                                                                                                                                                                 |
| DB_DATABASE                              | okapi_modules                |  false   | Postgres database name                                                                                                                                                                                     |
| MODULE_URL                               | http://mgr-applications:8081 |  false   | Module URL (module cannot define url for Kong registration by itself, because it can be under Load Balancer, so this value must be provided manually)                                                      |
| okapi.url                                | -                            |  false   | Okapi URL used to perform HTTP requests by `OkapiClient`.                                                                                                                                                  |
| OKAPI_INTEGRATION_ENABLED                | true                         |  false   | Defines if okapi integration is enabled or disabled.<br/>If it set to `false` - it will exclude all okapi-related beans from spring context.                                                               |
| tenant.url                               | -                            |   true   | Tenant URL used to perform HTTP requests by `TenantManagerClient`.                                                                                                                                         |
| kong.url                                 | -                            |   true   | Okapi URL used to perform HTTP requests for recurring jobs, required.                                                                                                                                      |
| KONG_ADMIN_URL                           | -                            |  false   | Alias for `kong.url`.                                                                                                                                                                                      |
| KONG_INTEGRATION_ENABLED                 | true                         |  false   | Defines if kong integration is enabled or disabled.<br/>If it set to `false` - it will exclude all kong-related beans from spring context.                                                                 |
| KONG_CONNECT_TIMEOUT                     | -                            |  false   | Defines the timeout in milliseconds for establishing a connection from Kong to upstream service. If the value is not provided then Kong defaults are applied.                                              |
| KONG_READ_TIMEOUT                        | -                            |  false   | Defines the timeout in milliseconds between two successive read operations for transmitting a request from Kong to the upstream service. If the value is not provided then Kong defaults are applied.      |
| KONG_WRITE_TIMEOUT                       | -                            |  false   | Defines the timeout in milliseconds between two successive write operations for transmitting a request from Kong to the upstream service. If the value is not provided then Kong defaults are applied.     |
| KONG_RETRIES                             | -                            |  false   | Defines the number of retries to execute upon failure to proxy. If the value is not provided then Kong defaults are applied.                                                                               |
| KONG_TLS_ENABLED                         | false                        |  false   | Allows to enable/disable TLS connection to Kong.                                                                                                                                                           |
| KONG_TLS_TRUSTSTORE_PATH                 | -                            |  false   | Truststore file path for TLS connection to Kong.                                                                                                                                                           |
| KONG_TLS_TRUSTSTORE_PASSWORD             | -                            |  false   | Truststore password for TLS connection to Kong.                                                                                                                                                            |
| KONG_TLS_TRUSTSTORE_TYPE                 | -                            |  false   | Truststore file type for TLS connection to Kong.                                                                                                                                                           |
| ENV                                      | folio                        |  false   | The logical name of the deployment (kafka topic prefix), must be unique across all environments using the same shared Kafka/Elasticsearch clusters, `a-z (any case)`, `0-9`, `-`, `_` symbols only allowed |
| KAFKA_HOST                               | kafka                        |  false   | Kafka broker hostname                                                                                                                                                                                      |
| KAFKA_PORT                               | 9092                         |  false   | Kafka broker port                                                                                                                                                                                          |
| KAFKA_SECURITY_PROTOCOL                  | PLAINTEXT                    |  false   | Kafka security protocol used to communicate with brokers (SSL or PLAINTEXT)                                                                                                                                |
| KAFKA_SSL_KEYSTORE_LOCATION              | -                            |  false   | The location of the Kafka key store file. This is optional for client and can be used for two-way authentication for client.                                                                               |
| KAFKA_SSL_KEYSTORE_PASSWORD              | -                            |  false   | The store password for the Kafka key store file. This is optional for client and only needed if 'ssl.keystore.location' is configured.                                                                     |
| KAFKA_SSL_TRUSTSTORE_LOCATION            | -                            |  false   | The location of the Kafka trust store file.                                                                                                                                                                |
| KAFKA_SSL_TRUSTSTORE_PASSWORD            | -                            |  false   | The password for the Kafka trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled.                                                 |
| KAFKA_DISCOVERY_TOPIC_PARTITIONS         | 1                            |  false   | Amount of partitions for `discovery` topic.                                                                                                                                                                |
| KAFKA_DISCOVERY_TOPIC_REPLICATION_FACTOR | -                            |  false   | Replication factor for `discovery` topic.                                                                                                                                                                  |
| TE_URL                                   | -                            |   true   | Tenant Entitlement URL used to perform HTTP requests by `TenantEntitlementClient`.                                                                                                                         |
| TE_TLS_ENABLED                           | false                        |  false   | Allows to enable/disable TLS connection to mgr-tenant-entitlements module.                                                                                                                                 |
| TE_TLS_TRUSTSTORE_PATH                   | -                            |  false   | Truststore file path for TLS connection to mgr-tenant-entitlements module.                                                                                                                                 |
| TE_TLS_TRUSTSTORE_PASSWORD               | -                            |  false   | Truststore password for TLS connection to mgr-tenant-entitlements module.                                                                                                                                  |
| TE_TLS_TRUSTSTORE_TYPE                   | -                            |  false   | Truststore file type for TLS connection to mgr-tenant-entitlements module.                                                                                                                                 |
| SECURITY_ENABLED                         | true                         |  false   | Allows to enable/disable security. If true and KC_INTEGRATION_ENABLED is also true - the Keycloak will be used as a security provider.                                                               |
| FAR_MODE                                 | false                        |  false   | Allows to enable Folio Application Registry mode, if FAR mode is enabled, kong integration must disabled using environment variable `KONG_INTEGRATION_ENABLED`.                                            |
| MOD_AUTHTOKEN_URL                        | -                            |   true   | Mod-authtoken URL. Required if OKAPI_INTEGRATION_ENABLED is true and SECURITY_ENABLED is true and KC_INTEGRATION_ENABLED is false.                                                                         |
| SECRET_STORE_TYPE                        | -                            |   true   | Secure storage type. Supported values: `EPHEMERAL`, `AWS_SSM`, `VAULT`, `FSSP`                                                                                                                             |
| VALIDATION_MODE                          | basic                        |  false   | Validation mode applied during Application Descriptors checking (see POST `/applications/validate` endpoint). Possible values: `none`, `basic`, `onCreate`                                                 |
| MAX_HTTP_REQUEST_HEADER_SIZE             | 200KB                        |   true   | Maximum size of the HTTP request header.                                                                                                                                                                   |
| REGISTER_MODULE_IN_KONG                  | true                         |  false   | Defines if module must be registered in Kong (it will create for itself service and list of routes from module descriptor)                                                                                 |
| ROUTER_PATH_PREFIX                       |                              |  false   | Defines routes prefix to be added to the generated endpoints by OpenAPI generator (`/foo/entites` -> `{{prefix}}/foo/entities`). Required if load balancing group has format like `{{host}}/{{moduleId}}`  |
| ROUTEMANAGEMENT_ENABLE                   | true                         |  false   | Enable Kong routes management for modules discovery information (e.g. creation of routes on module discovery creation/update, removal of routes on deletion of module discovery information)               |

### SSL Configuration environment variables

| Name                          | Default value | Required | Description                                                            |
|:------------------------------|:--------------|:--------:|:-----------------------------------------------------------------------|
| SERVER_PORT                   | 8081          |  false   | Server HTTP port. Should be specified manually in case of SSL enabled. |
| SERVER_SSL_ENABLED            | false         |  false   | Manage server's mode. If `true` then SSL will be enabled.              |
| SERVER_SSL_KEY_STORE          |               |  false   | Path to the keystore.  Mandatory if `SERVER_SSL_ENABLED` is `true`.    |
| SERVER_SSL_KEY_STORE_TYPE     | BCFKS         |  false   | Type of the keystore. By default `BCFKS` value is used.                |
| SERVER_SSL_KEY_STORE_PROVIDER | BCFIPS        |  false   | Provider of the keystore.                                              |
| SERVER_SSL_KEY_STORE_PASSWORD |               |  false   | Password for keystore.                                                 |
| SERVER_SSL_KEY_PASSWORD       |               |  false   | Password for key in keystore.                                          |

### Secure storage environment variables

#### AWS-SSM

Required when `SECRET_STORE_TYPE=AWS_SSM`

| Name                                          | Default value | Description                                                                                                                                                    |
|:----------------------------------------------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SECRET_STORE_AWS_SSM_REGION                   | -             | The AWS region to pass to the AWS SSM Client Builder. If not set, the AWS Default Region Provider Chain is used to determine which region to use.              |
| SECRET_STORE_AWS_SSM_USE_IAM                  | true          | If true, will rely on the current IAM role for authorization instead of explicitly providing AWS credentials (access_key/secret_key)                           |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT | -             | The HTTP endpoint to use for retrieving AWS credentials. This is ignored if useIAM is true                                                                     |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH     | -             | The path component of the credentials endpoint URI. This value is appended to the credentials endpoint to form the URI from which credentials can be obtained. |

#### Vault

Required when `SECRET_STORE_TYPE=VAULT`

| Name                                    | Default value | Description                                                                         |
|:----------------------------------------|:--------------|:------------------------------------------------------------------------------------|
| SECRET_STORE_VAULT_TOKEN                | -             | token for accessing vault, may be a root token                                      |
| SECRET_STORE_VAULT_ADDRESS              | -             | the address of your vault                                                           |
| SECRET_STORE_VAULT_ENABLE_SSL           | false         | whether or not to use SSL                                                           |
| SECRET_STORE_VAULT_PEM_FILE_PATH        | -             | the path to an X.509 certificate in unencrypted PEM format, using UTF-8 encoding    |
| SECRET_STORE_VAULT_KEYSTORE_PASSWORD    | -             | the password used to access the JKS keystore (optional)                             |
| SECRET_STORE_VAULT_KEYSTORE_FILE_PATH   | -             | the path to a JKS keystore file containing a client cert and private key            |
| SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH | -             | the path to a JKS truststore file containing Vault server certs that can be trusted |

#### Folio Secure Store Proxy (FSSP)

Required when `SECRET_STORE_TYPE=FSSP`

| Name                                   | Default value         | Description                                          |
|:---------------------------------------|:----------------------|:-----------------------------------------------------|
| SECRET_STORE_FSSP_ADDRESS              | -                     | The address (URL) of the FSSP service.               |
| SECRET_STORE_FSSP_SECRET_PATH          | secure-store/entries  | The path in FSSP where secrets are stored/retrieved. |
| SECRET_STORE_FSSP_ENABLE_SSL           | false                 | Whether to use SSL when connecting to FSSP.          |
| SECRET_STORE_FSSP_TRUSTSTORE_PATH      | -                     | Path to the truststore file for SSL connections.     |
| SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE | -                     | The type of the truststore file (e.g., JKS, PKCS12). |
| SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD  | -                     | The password for the truststore file.                |

## Keycloak Integration

#### Import Keycloak data on startup

As startup, the application creates/updates necessary records in Keycloak from the internal module descriptor:

- Resource server
- Client - with credentials of `KC_CLIENT_ID`, a client secret will be retrieved from the secure store.
- Resources - mapped from descriptor routing entries.
- Permissions - mapped from `requiredPermissions` of routing entries.
- Roles - mapped from permission sets of descriptor.
- Policies - role policies as well as aggregate policies (specific for each resource).

#### Keycloak Security

Keycloak can be used as a security provider. If enabled - application will delegate endpoint permissions evaluation to
Keycloak.
A valid Keycloak JWT token must be passed for accessing secured resources.
The feature is controlled by two env variables `SECURITY_ENABLED` and `KC_INTEGRATION_ENABLED`.

### Keycloak specific environment variables

| Name                              | Default value              |  Required   | Description                                                                                                                                             |
|:----------------------------------|:---------------------------|:-----------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------|
| KC_URL                            | http://keycloak:8080       |    false    | Keycloak URL used to perform HTTP requests.                                                                                                             |
| KC_INTEGRATION_ENABLED            | true                       |    false    | Defines if Keycloak integration is enabled or <br/>disabled.<br/>If it set to `false` - it will exclude all keycloak-related beans from spring context. |
| KC_IMPORT_ENABLED                 | false                      |    false    | If true - at startup, register/create necessary records in keycloak from the internal module descriptor.                                                |
| KC_ADMIN_CLIENT_ID                | folio-backend-admin-client |    false    | Keycloak client id. Used for register/create necessary records in keycloak from the internal module descriptor.                                         |
| KC_ADMIN_CLIENT_SECRET            | -                          | conditional | Keycloak client secret. Required only if admin username/password are not set.                                                                           |
| KC_ADMIN_USERNAME                 | -                          | conditional | Keycloak admin username. Required only if admin secret is not set.                                                                                      |
| KC_ADMIN_PASSWORD                 | -                          | conditional | Keycloak admin password. Required only if admin secret is not set.                                                                                      |
| KC_ADMIN_GRANT_TYPE               | client_credentials         |    false    | Keycloak admin grant type. Should be set to `password` if username/password are used instead of client secret.                                          |
| KC_CLIENT_ID                      | mgr-applications           |    false    | client id to be imported to Keycloak.                                                                                                                   |
| KC_CLIENT_TLS_ENABLED             | false                      |    false    | Enables TLS for keycloak clients.                                                                                                                       |
| KC_CLIENT_TLS_TRUSTSTORE_PATH     | -                          |    false    | Truststore file path for keycloak clients.                                                                                                              |
| KC_CLIENT_TLS_TRUSTSTORE_PASSWORD | -                          |    false    | Truststore password for keycloak clients.                                                                                                               |
| KC_AUTH_TOKEN_VALIDATE_URI        | false                      |    false    | Defines if validation for JWT must be run to compare configuration URL and token issuer for keycloak.                                                   |
| KC_JWKS_REFRESH_INTERVAL          | 60                         |    false    | Jwks refresh interval for realm JWT parser (in minutes).                                                                                                |
| KC_FORCED_JWKS_REFRESH_INTERVAL   | 60                         |    false    | Forced jwks refresh interval for realm JWT parser (used in signing key rotation, in minutes).                                                           |

## Kong Gateway Integration

Kong gateway integration implemented using idempotent approach
with [Kong Admin API](https://docs.konghq.com/gateway/latest/admin-api/).

### Kong Service Registration

The Kong Gateway services are added on service discovery registration per application. Each Kong Service has tag equal
to `applicationId` to improve observability. Tags can be used to filter core entities, via the `?tags` querystring
parameter.

Kong Services per application in kong can be found using a following HTTP request:

```shell
curl -XGET "$KONG_ADMIN_URL/services?tags=$applicationId"
```

### Kong Route Registration

The Kong routes registered per-tenant using header filter:

```json
{
  "headers": { "x-okapi-tenant": [ "$tenantId" ] }
}
```

Routes as well populated with tags: `moduleId` and `tenantId` to be filtered.

Routes per tenant can be found with:

```shell
curl -XGET "$KONG_ADMIN_URL/routes?tags=$moduleId,$tenantId"
```

or

```shell
curl -XGET "$KONG_ADMIN_URL/services/$moduleId/routes?tags=$tenantId"
```

## Kafka Integration
### Events upon discovery changes
* The application publishes lightweight kafka events when discovery information is registered/updated/removed.
* The topic is being created on application startup.
#### Naming convention
topic naming convention:`<prefix>_discovery`
* Prefix is passed in as environment variable (for FSE it's usually the cluster identifier, e.g. "evrk")
#### Event structure
```json
{
  "moduleId": "mod-foo-1.2.3"
}
```

## Manager Tenant Entitlements Integration

* The application checks if application descriptor exist in mgr-tenant-entitlements before deletion

```shell
curl -XGET "$TE_URL/entitlements?query=applicationId=$applicationId"
```

## Folio Application Registry mode

In this mode, we only need a subset of the component's functionality.
While CRUD operations for application descriptors works as-is,
the integrations with Kafka, Kong, Okapi, mgr-tenant-entitlements is disabled.
To enable this mode set `FAR_MODE` env variable to `true` and make sure to leave other integration variables unset or
set to `false`.
