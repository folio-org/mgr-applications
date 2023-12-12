INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('test-app-1.0.0', 'test-disc-app', '1.0.0', '{
    "id": "test-app-1.0.0",
    "name": "test-app",
    "version": "1.0.0",
    "modules": [
      {"id": "test-module-foo-1.0.0", "name": "test-module-foo", "version": "1.0.0"},
      {"id": "test-module-baz-1.0.0", "name": "test-module-baz", "version": "1.0.0"},
      {"id": "test-module-bar-1.0.0", "name": "test-module-bar", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, descriptor, discovery_url)
VALUES
    ('test-module-foo-1.0.0', 'test-module-foo', '1.0.0', '{}', NULL),
    ('test-module-bar-1.0.0', 'test-module-bar', '1.0.0', '{}', NULL),
    ('test-module-baz-1.0.0', 'test-module-baz', '1.0.0', '{}', NULL);

INSERT INTO application_module(application_id, module_id)
VALUES
    ('test-app-1.0.0', 'test-module-foo-1.0.0'),
    ('test-app-1.0.0', 'test-module-bar-1.0.0'),
    ('test-app-1.0.0', 'test-module-baz-1.0.0');
