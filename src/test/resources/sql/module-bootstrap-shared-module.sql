INSERT INTO application(id, name, version, application_descriptor) VALUES
  ('app-a-1.0.0', 'app-a', '1.0.0', '{"id": "app-a-1.0.0"}'),
  ('app-b-1.0.0', 'app-b', '1.0.0', '{"id": "app-b-1.0.0"}');

INSERT INTO module(id, name, version, discovery_url, descriptor, type) VALUES
  ('mod-foo-1.0.0', 'mod-foo', '1.0.0', 'http://mod-foo:8080', '{}', 'BACKEND'),
  ('mod-bar-1.0.0', 'mod-bar', '1.0.0', 'http://mod-bar:8080', '{}', 'BACKEND');

INSERT INTO application_module(application_id, module_id) VALUES
  ('app-a-1.0.0', 'mod-foo-1.0.0'),
  ('app-a-1.0.0', 'mod-bar-1.0.0'),
  ('app-b-1.0.0', 'mod-bar-1.0.0');

INSERT INTO module_interface_reference(module_id, id, version, type) VALUES
  ('mod-foo-1.0.0', 'bar-int', '1.0', 'REQUIRES'),
  ('mod-bar-1.0.0', 'bar-int', '1.0', 'PROVIDES');
