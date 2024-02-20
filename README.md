# mgr-applications

Copyright (C) 2022-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Running It](#running-it)
* [Environment Variables](#environment-variables)
* [Keycloak Integration](#keycloak-integration)
* [Kong Gateway Integration](#kong-gateway-integration)
* [Kafka Integration](#kafka-integration)
* [Manager Tenant Entitlements Integration](#mgr-tenant-entitlement-integration)
* [Folio Application Registry Mode](#folio-application-registry-mode)

## Introduction

`mgr-applications` provides following functionality:

* Dependency check / platform integrity validation
* (De-)Registration of applications
* Enabling/disabling of an application (including dependencies)
* (Un-)Deployment of an application (optional)
* Application health and availability monitoring
* Optional integration with Kong gateway
  * Add/remove services on application discovery update
  * Add/remove routes per tenant on application install/uninstall

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
| SECURITY_ENABLED                         | false                        |  false   | Allows to enable/disable security. If true and KEYCLOAK_INTEGRATION_ENABLED is also true - the Keycloak will be used as a security provider.                                                               |
| FAR_MODE                                 | false                        |  false   | Allows to enable Folio Application Registry mode, if FAR mode is enabled, kong integration must disabled using environment variable `KONG_INTEGRATION_ENABLED`.                                            |
| MOD_AUTHTOKEN_URL                        | -                            |   true   | Mod-authtoken URL. Required if OKAPI_INTEGRATION_ENABLED is true and SECURITY_ENABLED is true and KC_INTEGRATION_ENABLED is false.                                                                         |
| SECRET_STORE_TYPE                        | -                            |   true   | Secure storage type. Supported values: `EPHEMERAL`, `AWS_SSM`, `VAULT`                                                                                                                                     |
| VALIDATION_MODE                          | basic                        |  false   | Validation mode applied during Application Descriptors checking (see POST `/applications/validate` endpoint). Possible values: `none`, `basic`, `onCreate`                                                 |
| MAX_HTTP_REQUEST_HEADER_SIZE             | 200KB                        |   true   | Maximum size of the HTTP request header.                                                                                                                                                                   |
| REGISTER_MODULE_IN_KONG                  | true                         |  false   | Defines if module must be registered in Kong (it will create for itself service and list of routes from module descriptor)                                                                                 |
| ROUTER_PATH_PREFIX                       |                              |  false   | Defines routes prefix to be added to the generated endpoints by OpenAPI generator (`/foo/entites` -> `{{prefix}}/foo/entities`). Required if load balancing group has format like `{{host}}/{{moduleId}}`  |

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

### Keycloak Integration

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
The feature is controlled by two env variables `SECURITY_ENABLED` and `KEYCLOAK_INTEGRATION_ENABLED`.

### Keycloak specific environment variables

| Name                    | Default value               |  Required   | Description                                                                                                                                             |
|:------------------------|:----------------------------|:-----------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------|
| KC_URL                  | http://keycloak:8080        |    false    | Keycloak URL used to perform HTTP requests.                                                                                                             |
| KC_INTEGRATION_ENABLED  | false                       |    false    | Defines if Keycloak integration is enabled or <br/>disabled.<br/>If it set to `false` - it will exclude all keycloak-related beans from spring context. |
| KC_IMPORT_ENABLED       | false                       |    false    | If true - at startup, register/create necessary records in keycloak from the internal module descriptor.                                                |
| KC_ADMIN_CLIENT_ID      | folio-backend-admin-client  |    false    | Keycloak client id. Used for register/create necessary records in keycloak from the internal module descriptor.                                         |
| KC_ADMIN_CLIENT_SECRET  | -                           | conditional | Keycloak client secret. Required only if admin username/password are not set.                                                                           |
| KC_ADMIN_USERNAME       | -                           | conditional | Keycloak admin username. Required only if admin secret is not set.                                                                                      |
| KC_ADMIN_PASSWORD       | -                           | conditional | Keycloak admin password. Required only if admin secret is not set.                                                                                      |
| KC_ADMIN_GRANT_TYPE     | client_credentials          |    false    | Keycloak admin grant type. Should be set to `password` if username/password are used instead of client secret.                                          |
| KC_CLIENT_ID            | mgr-applications            |    false    | client id to be imported to Keycloak.                                                                                                                   |

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
