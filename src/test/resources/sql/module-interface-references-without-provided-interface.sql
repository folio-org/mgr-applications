-- Modules and interface references as they exist before the provided_interface backfill: PROVIDES rows are present,
-- but their provided_interface column is still NULL.
INSERT INTO module(id, name, version, descriptor, type)
VALUES
  ('mod-a-1.0.0', 'mod-a', '1.0.0', '{
    "id": "mod-a-1.0.0",
    "provides": [
      {"id": "api-a", "version": "1.0", "interfaceType": "multiple",
       "handlers": [{"methods": ["GET"], "pathPattern": "/a"}]},
      {"id": "api-b", "version": "2.0"}
    ]
  }', 'BACKEND'),
  -- no provides at all
  ('mod-no-provides-1.0.0', 'mod-no-provides', '1.0.0', '{}', 'BACKEND'),
  -- provides is not an array
  ('mod-invalid-provides-1.0.0', 'mod-invalid-provides', '1.0.0', '{"provides": {"id": "api-x"}}', 'BACKEND');

INSERT INTO module_interface_reference(module_id, id, version, type)
VALUES
  ('mod-a-1.0.0', 'api-a', '1.0', 'PROVIDES'),
  ('mod-a-1.0.0', 'api-b', '2.0', 'PROVIDES'),
  -- a PROVIDES row that has no matching element in the module descriptor
  ('mod-a-1.0.0', 'api-without-element', '1.0', 'PROVIDES'),
  ('mod-a-1.0.0', 'api-required', '1.0', 'REQUIRES'),
  ('mod-a-1.0.0', 'api-optional', '1.0', 'OPTIONAL'),
  ('mod-no-provides-1.0.0', 'api-c', '1.0', 'PROVIDES'),
  ('mod-invalid-provides-1.0.0', 'api-x', '1.0', 'PROVIDES');
