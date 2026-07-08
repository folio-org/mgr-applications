insert into module_interface_reference(module_id, id, version, type) VALUES
  ('test-module-foo-1.0.0', 'test-bar-interface', '1.0', 'REQUIRES'),
  ('test-module-bar-1.0.0', 'test-bar-interface', '1.0', 'PROVIDES'),
  ('test-module-foo-1.0.0', 'test-baz-interface', '1.0', 'REQUIRES'),
  ('test-module-baz-1.0.0', 'test-baz-interface', '1.0', 'PROVIDES'),
  -- test-module-bar provides a SECOND interface that test-module-foo also requires. With the bootstrap queries no
  -- longer using DISTINCT, this guards that the provider row is still returned once (the IN subquery is a semi-join
  -- and must not fan bar out to two rows).
  ('test-module-foo-1.0.0', 'test-bar-interface-2', '1.0', 'REQUIRES'),
  ('test-module-bar-1.0.0', 'test-bar-interface-2', '1.0', 'PROVIDES');
