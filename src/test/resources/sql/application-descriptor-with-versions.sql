-- Test data for latest and preRelease parameter testing
-- Application 'my-app' with multiple versions including pre-releases using project version format
insert into application(id, name, version, application_descriptor)
values
  ('my-app-8.0.1', 'my-app', '8.0.1', '{
    "id": "my-app-8.0.1",
    "name": "my-app",
    "version": "8.0.1",
    "modules": [
      { "id": "mod-core-8.0.1", "name": "mod-core", "version": "8.0.1" }
    ]
  }'),
  ('my-app-8.1.0-SNAPSHOT.2245', 'my-app', '8.1.0-SNAPSHOT.2245', '{
    "id": "my-app-8.1.0-SNAPSHOT.2245",
    "name": "my-app",
    "version": "8.1.0-SNAPSHOT.2245",
    "modules": [
      { "id": "mod-core-8.1.0", "name": "mod-core", "version": "8.1.0" }
    ]
  }'),
  ('my-app-8.1.0', 'my-app', '8.1.0', '{
    "id": "my-app-8.1.0",
    "name": "my-app",
    "version": "8.1.0",
    "modules": [
      { "id": "mod-core-8.1.0", "name": "mod-core", "version": "8.1.0" }
    ]
  }'),
  ('my-app-9.0.0-SNAPSHOT.3456', 'my-app', '9.0.0-SNAPSHOT.3456', '{
    "id": "my-app-9.0.0-SNAPSHOT.3456",
    "name": "my-app",
    "version": "9.0.0-SNAPSHOT.3456",
    "modules": [
      { "id": "mod-core-9.0.0", "name": "mod-core", "version": "9.0.0" }
    ]
  }'),
  ('my-app-9.0.0-SNAPSHOT.4012', 'my-app', '9.0.0-SNAPSHOT.4012', '{
    "id": "my-app-9.0.0-SNAPSHOT.4012",
    "name": "my-app",
    "version": "9.0.0-SNAPSHOT.4012",
    "modules": [
      { "id": "mod-core-9.0.0", "name": "mod-core", "version": "9.0.0" }
    ]
  }'),
  ('my-app-9.0.1', 'my-app', '9.0.1', '{
    "id": "my-app-9.0.1",
    "name": "my-app",
    "version": "9.0.1",
    "modules": [
      { "id": "mod-core-9.0.1", "name": "mod-core", "version": "9.0.1" }
    ]
  }');

-- Corresponding modules
INSERT INTO module(id, name, version, descriptor, type)
VALUES
  ('mod-core-8.0.1', 'mod-core', '8.0.1', '{
    "id": "mod-core-8.0.1",
    "name": "core",
    "provides": [ { "id": "int-core", "version": "8.0" } ]
  }', 'BACKEND'),
  ('mod-core-8.1.0', 'mod-core', '8.1.0', '{
    "id": "mod-core-8.1.0",
    "name": "core",
    "provides": [ { "id": "int-core", "version": "8.1" } ]
  }', 'BACKEND'),
  ('mod-core-9.0.0', 'mod-core', '9.0.0', '{
    "id": "mod-core-9.0.0",
    "name": "core",
    "provides": [ { "id": "int-core", "version": "9.0" } ]
  }', 'BACKEND'),
  ('mod-core-9.0.1', 'mod-core', '9.0.1', '{
    "id": "mod-core-9.0.1",
    "name": "core",
    "provides": [ { "id": "int-core", "version": "9.0" } ]
  }', 'BACKEND');

-- Module associations
INSERT INTO application_module(application_id, module_id)
VALUES
  ('my-app-8.0.1', 'mod-core-8.0.1'),
  ('my-app-8.1.0-SNAPSHOT.2245', 'mod-core-8.1.0'),
  ('my-app-8.1.0', 'mod-core-8.1.0'),
  ('my-app-9.0.0-SNAPSHOT.3456', 'mod-core-9.0.0'),
  ('my-app-9.0.0-SNAPSHOT.4012', 'mod-core-9.0.0'),
  ('my-app-9.0.1', 'mod-core-9.0.1');
