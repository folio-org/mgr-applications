INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('test-app-1.0.0', 'test-app', '1.0.0', '{
    "id": "test-app-1.0.0",
    "name": "test-app",
    "version": "1.0.0",
    "uiModules": [
      {"id": "test-ui-module-1.0.0", "name": "test-ui-module", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, descriptor, discovery_url, type)
VALUES
    ('test-ui-module-1.0.0', 'test-ui-module', '1.0.0', '{}', NULL, 'UI');

INSERT INTO application_module(application_id, module_id)
VALUES
    ('test-app-1.0.0', 'test-ui-module-1.0.0');
