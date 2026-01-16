INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('test-app-1.0.0', 'test-app', '1.0.0', '{
    "id": "test-app-1.0.0",
    "name": "test-app",
    "version": "1.0.0",
    "modules": [
      {"id": "test-module-foo-1.0.0", "name": "test-module-foo", "version": "1.0.0"}
    ],
    "uiModules": [
      {"id": "test-ui-module-1.0.0", "name": "test-ui-module", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
    ('test-module-foo-1.0.0', 'test-module-foo', '1.0.0', 'http://test-module-foo:8080', '{}', 'BACKEND'),
    ('test-ui-module-1.0.0', 'test-ui-module', '1.0.0', 'http://test-ui-module:8080', '{}', 'UI');

INSERT INTO application_module(application_id, module_id)
VALUES
    ('test-app-1.0.0', 'test-module-foo-1.0.0'),
    ('test-app-1.0.0', 'test-ui-module-1.0.0');
