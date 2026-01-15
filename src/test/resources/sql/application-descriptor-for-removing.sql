insert into application(id, name, version, application_descriptor)
values
  ('test-app-1.0.0', 'test-app', '1.0.0', '{
    "id": "test-app-1.0.0",
    "name": "test-app",
    "version": "1.0.0",
    "modules": [
      { "id": "mod-foo-1.0.0", "name": "mod-foo", "version": "1.0.0" },
      { "id": "mod-bar-1.0.0", "name": "mod-bar", "version": "1.0.0" }
    ]
  }'),
    ('test-app-2.0.0', 'test-app', '2.0.0', '{
    "id": "test-app-2.0.0",
    "name": "test-app",
    "version": "2.0.0",
    "modules": [
      { "id": "mod-foo-1.0.0", "name": "mod-foo", "version": "1.0.0" },
      { "id": "mod-bar-1.0.1", "name": "mod-bar", "version": "1.0.1" }
    ]
  }'),
  ('test-app-3.0.0', 'test-app', '3.0.0', '{
    "id": "test-app-3.0.0",
    "name": "test-app",
    "version": "3.0.0",
    "uiModules": [
      { "id": "ui-foo-1.0.0", "name": "ui-foo", "version": "1.0.0" },
      { "id": "ui-bar-1.0.0", "name": "ui-bar", "version": "1.0.0" }
    ]
  }'),
    ('test-app-4.0.0', 'test-app', '4.0.0', '{
    "id": "test-app-4.0.0",
    "name": "test-app",
    "version": "4.0.0",
    "uiModules": [
      { "id": "ui-foo-1.0.0", "name": "ui-foo", "version": "1.0.0" },
      { "id": "ui-bar-1.0.1", "name": "ui-bar", "version": "1.0.1" }
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
  ('mod-foo-1.0.0', 'mod-foo', '1.0.0', 'http://mod-foo:8080', '{
    "id": "mod-foo-1.0.0",
    "name": "foo",
    "provides": [ { "id": "int-foo", "version": "1.0" } ]
  }', 'BACKEND'),
  ('mod-bar-1.0.0', 'mod-bar', '1.0.0', 'http://mod-bar:8080', '{
    "id": "mod-bar-1.0.0",
    "name": "bar",
    "provides": [ { "id": "int-bar", "version": "1.0" } ],
    "requires": [ { "id": "int-foo", "version": "1.0" } ]
  }', 'BACKEND'),
  ('mod-bar-1.0.1', 'mod-bar', '1.0.1', 'http://mod-bar:8081', '{
    "id": "mod-bar-1.0.1",
    "name": "bar",
    "provides": [ { "id": "int-bar", "version": "1.1" } ],
    "requires": [ { "id": "int-foo", "version": "1.1" } ]
  }', 'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES ('test-app-1.0.0', 'mod-foo-1.0.0'),
       ('test-app-1.0.0', 'mod-bar-1.0.0'),
       ('test-app-2.0.0', 'mod-foo-1.0.0'),
       ('test-app-2.0.0', 'mod-bar-1.0.1');

INSERT INTO module(id, name, version, descriptor, type)
VALUES
  ('ui-foo-1.0.0', 'ui-foo', '1.0.0', '{
    "id": "ui-foo-1.0.0",
    "name": "foo",
    "requires": [ { "id": "int-foo", "version": "1.0" } ]
  }', 'UI'),
  ('ui-bar-1.0.0', 'ui-bar', '1.0.0', '{
    "id": "ui-bar-1.0.0",
    "name": "bar",
    "requires": [ { "id": "int-bar", "version": "1.0" } ]
  }', 'UI'),
  ('ui-bar-1.0.1', 'ui-bar', '1.0.1', '{
    "id": "ui-bar-1.0.1",
    "name": "bar",
    "requires": [ { "id": "int-bar", "version": "1.1" } ]
  }', 'UI');

INSERT INTO application_module(application_id, module_id)
VALUES ('test-app-3.0.0', 'ui-foo-1.0.0'),
       ('test-app-3.0.0', 'ui-bar-1.0.0'),
       ('test-app-4.0.0', 'ui-foo-1.0.0'),
       ('test-app-4.0.0', 'ui-bar-1.0.1');
