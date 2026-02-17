-- App 1 with 2 modules having discovery URLs
INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('app-one-1.0.0', 'app-one', '1.0.0', '{
    "id": "app-one-1.0.0",
    "name": "app-one",
    "version": "1.0.0",
    "modules": [
      {"id": "mod-a-1.0.0", "name": "mod-a", "version": "1.0.0"},
      {"id": "mod-b-1.0.0", "name": "mod-b", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
    ('mod-a-1.0.0', 'mod-a', '1.0.0', 'http://mod-a:8080', '{}', 'BACKEND'),
    ('mod-b-1.0.0', 'mod-b', '1.0.0', 'http://mod-b:8080', '{}', 'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES
    ('app-one-1.0.0', 'mod-a-1.0.0'),
    ('app-one-1.0.0', 'mod-b-1.0.0');

-- App 2 with 1 module having discovery URL
INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('app-two-1.0.0', 'app-two', '1.0.0', '{
    "id": "app-two-1.0.0",
    "name": "app-two",
    "version": "1.0.0",
    "modules": [
      {"id": "mod-c-1.0.0", "name": "mod-c", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
    ('mod-c-1.0.0', 'mod-c', '1.0.0', 'http://mod-c:8080', '{}', 'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES
    ('app-two-1.0.0', 'mod-c-1.0.0');

-- App 3 with no discovery URLs (should be excluded from results)
INSERT INTO application(id, name, version, application_descriptor)
VALUES
  ('app-three-1.0.0', 'app-three', '1.0.0', '{
    "id": "app-three-1.0.0",
    "name": "app-three",
    "version": "1.0.0",
    "modules": [
      {"id": "mod-d-1.0.0", "name": "mod-d", "version": "1.0.0"}
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
    ('mod-d-1.0.0', 'mod-d', '1.0.0', NULL, '{}', 'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES
    ('app-three-1.0.0', 'mod-d-1.0.0');
