-- The SAME unchanged provider module (mod-shared-1.0.0) is pinned by two versions of the same-named application
-- (app-foo-1.0.0 and app-foo-2.0.0). Different tenants can entitle either version. The egress bootstrap must find
-- the shared provider for a tenant scoped to EITHER application version, not just one arbitrarily-chosen one.
INSERT INTO application(id, name, version, application_descriptor) VALUES
  ('app-consumer-1.0.0', 'app-consumer', '1.0.0', '{"id": "app-consumer-1.0.0"}'),
  ('app-foo-1.0.0', 'app-foo', '1.0.0', '{"id": "app-foo-1.0.0"}'),
  ('app-foo-2.0.0', 'app-foo', '2.0.0', '{"id": "app-foo-2.0.0"}');

INSERT INTO module(id, name, version, discovery_url, descriptor, type) VALUES
  ('mod-consumer-1.0.0', 'mod-consumer', '1.0.0', 'http://mod-consumer:8080',
    '{"id": "mod-consumer-1.0.0", "requires": [{"id": "shared-int", "version": "1.0"}]}', 'BACKEND'),
  ('mod-shared-1.0.0', 'mod-shared', '1.0.0', 'http://mod-shared:8080',
    '{"id": "mod-shared-1.0.0", "provides": [{"id": "shared-int", "version": "1.0", "interfaceType": "multiple"}]}',
    'BACKEND');

INSERT INTO application_module(application_id, module_id) VALUES
  ('app-consumer-1.0.0', 'mod-consumer-1.0.0'),
  ('app-foo-1.0.0', 'mod-shared-1.0.0'),
  ('app-foo-2.0.0', 'mod-shared-1.0.0');

INSERT INTO module_interface_reference(module_id, id, version, type) VALUES
  ('mod-consumer-1.0.0', 'shared-int', '1.0', 'REQUIRES'),
  ('mod-shared-1.0.0', 'shared-int', '1.0', 'PROVIDES');
