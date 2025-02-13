insert into application(id, name, version, application_descriptor)
values
  ('test-app-1.0.0', 'test-app', '1.0.0', '{
    "id": "test-app-1.0.0",
    "name": "test-app",
    "version": "1.0.0",
    "modules": [ {"id": "test-module-foo-1.0.0", "name": "test-module-foo", "version": "1.0.0"} ]
  }'),
  ('test-app-3.0.0', 'test-app', '3.0.0', '{
    "id": "test-app-3.0.0",
    "name": "test-app",
    "version": "3.0.0"
  }'),
  ('test-app-2.0.0', 'test-app', '2.0.0', '{
      "id": "test-app-2.0.0",
      "name": "test-app",
      "version": "2.0.0",
      "modules": [
        {"id": "test-module-bar-1.0.0", "name": "test-module-bar", "version": "1.0.0"},
        {"id": "test-module-baz-1.0.0", "name": "test-module-baz", "version": "1.0.0"}
      ],
      "dependencies": [
          {
            "name": "test-app",
            "version": "3.0.0"
          }
        ]
    }');

INSERT INTO module(id, name, version, discovery_url, descriptor)
VALUES
  ('test-module-foo-1.0.0', 'test-module-foo', '1.0.0', 'http://test-module-foo:8080', '{}'),
  ('test-module-bar-1.0.0', 'test-module-bar', '1.0.0', 'http://test-module-bar:8080', '{}'),
  ('test-module-baz-1.0.0', 'test-module-baz', '1.0.0', 'http://test-module-baz:8080', '{}');

INSERT INTO application_module(application_id, module_id)
VALUES ('test-app-1.0.0', 'test-module-foo-1.0.0'),
       ('test-app-2.0.0', 'test-module-bar-1.0.0'),
       ('test-app-2.0.0', 'test-module-baz-1.0.0');
