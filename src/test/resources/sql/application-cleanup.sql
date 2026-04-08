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
  }');

insert into module(id, name, version, discovery_url, descriptor, type)
values
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

insert into application_module(application_id, module_id)
values ('test-app-1.0.0', 'mod-foo-1.0.0'),
       ('test-app-1.0.0', 'mod-bar-1.0.0'),
       ('test-app-2.0.0', 'mod-foo-1.0.0'),
       ('test-app-2.0.0', 'mod-bar-1.0.1');
