INSERT INTO application(id, name, version, application_descriptor) VALUES
  ('app-consumer-1.0.0', 'app-consumer', '1.0.0', '{"id": "app-consumer-1.0.0"}'),
  ('app-prov-a-1.0.0', 'app-prov-a', '1.0.0', '{"id": "app-prov-a-1.0.0"}'),
  ('app-prov-b-1.0.0', 'app-prov-b', '1.0.0', '{"id": "app-prov-b-1.0.0"}');

INSERT INTO module(id, name, version, discovery_url, descriptor, type) VALUES
  ('mod-consumer-1.0.0', 'mod-consumer', '1.0.0', 'http://mod-consumer:8080',
    '{"id": "mod-consumer-1.0.0", "requires": [{"id": "shared-int", "version": "1.0"}]}', 'BACKEND'),
  ('mod-provider-1.0.0', 'mod-provider', '1.0.0', 'http://mod-provider-1:8080',
    '{"id": "mod-provider-1.0.0", "provides": [{"id": "shared-int", "version": "1.0", "interfaceType": "multiple"}]}', 'BACKEND'),
  ('mod-provider-2.0.0', 'mod-provider', '2.0.0', 'http://mod-provider-2:8080',
    '{"id": "mod-provider-2.0.0", "provides": [{"id": "shared-int", "version": "1.0", "interfaceType": "multiple"}]}', 'BACKEND');

INSERT INTO application_module(application_id, module_id) VALUES
  ('app-consumer-1.0.0', 'mod-consumer-1.0.0'),
  ('app-prov-a-1.0.0', 'mod-provider-1.0.0'),
  ('app-prov-b-1.0.0', 'mod-provider-2.0.0');

INSERT INTO module_interface_reference(module_id, id, version, type) VALUES
  ('mod-consumer-1.0.0', 'shared-int', '1.0', 'REQUIRES'),
  ('mod-provider-1.0.0', 'shared-int', '1.0', 'PROVIDES'),
  ('mod-provider-2.0.0', 'shared-int', '1.0', 'PROVIDES');
