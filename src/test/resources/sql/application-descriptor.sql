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
      { "id": "mod-foo-1.0.1", "name": "mod-foo", "version": "1.0.1" },
      { "id": "mod-bar-1.0.1", "name": "mod-bar", "version": "1.0.1" }
    ]
  }'),
  ('test-app-3.0.0', 'test-app', '3.0.0', '{
    "id": "test-app-3.0.0",
    "name": "test-app",
    "version": "3.0.0",
    "modules": [ { "id": "mod-foo-2.0.0", "name": "mod-foo", "version": "2.0.0" } ]
  }'),
  ('test-app-4.0.0', 'test-app', '4.0.0', '{
      "id": "test-app-4.0.0",
      "name": "test-app",
      "platform": "base",
      "version": "4.0.0",
      "modules": [ { "id": "mod-foo-2.0.0", "name": "mod-foo", "version": "2.0.0" } ]
    }');

INSERT INTO module(id, name, version, descriptor, type)
VALUES
  ('mod-foo-1.0.0', 'mod-foo', '1.0.0', '{
    "id": "mod-foo-1.0.0",
    "name": "foo",
    "provides": [ { "id": "int-foo", "version": "1.0" } ]
  }', 'BACKEND'),
  ('mod-foo-1.0.1', 'mod-foo', '1.0.1', '{
    "id": "mod-foo-1.0.1",
    "name": "foo",
    "provides": [ { "id": "int-foo", "version": "1.1" } ]
  }', 'BACKEND'),
  ('mod-bar-1.0.0', 'mod-bar', '1.0.0', '{
    "id": "mod-bar-1.0.0",
    "name": "bar",
    "provides": [ { "id": "int-bar", "version": "1.0" } ],
    "requires": [ { "id": "int-foo", "version": "1.0" } ]
  }', 'BACKEND'),
  ('mod-bar-1.0.1', 'mod-bar', '1.0.1', '{
    "id": "mod-bar-1.0.1",
    "name": "bar",
    "provides": [ { "id": "int-bar", "version": "1.1" } ],
    "requires": [ { "id": "int-foo", "version": "1.1" } ]
  }', 'BACKEND'),
  ('mod-foo-2.0.0', 'mod-foo', '2.0.0', '{
    "id": "mod-foo-2.0.0",
    "name": "foo",
    "provides": [ { "id": "int-foo", "version": "2.0" } ]
  }', 'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES ('test-app-1.0.0', 'mod-foo-1.0.0'),
       ('test-app-1.0.0', 'mod-bar-1.0.0'),
       ('test-app-2.0.0', 'mod-foo-1.0.1'),
       ('test-app-2.0.0', 'mod-bar-1.0.1'),
       ('test-app-3.0.0', 'mod-foo-2.0.0');
