insert into module_interface_reference(module_id, id, version, type, provided_interface) VALUES
  ('test-module-foo-1.0.0', 'test-bar-interface', '1.0', 'REQUIRES', null),
  ('test-module-bar-1.0.0', 'test-bar-interface', '1.0', 'PROVIDES', '{
    "id": "test-bar-interface", "version": "1.0", "interfaceType": "multiple",
    "handlers": [ {"methods": ["GET"], "pathPattern": "/bar", "permissionsDesired": ["bar.get"]} ]
  }'),
  ('test-module-foo-1.0.0', 'test-baz-interface', '1.0', 'REQUIRES', null),
  ('test-module-baz-1.0.0', 'test-baz-interface', '1.0', 'PROVIDES', '{
    "id": "test-baz-interface", "version": "1.0",
    "handlers": [ {"methods": ["POST"], "pathPattern": "/baz"} ]
  }'),
  -- test-module-bar provides a SECOND interface that test-module-foo also requires. With the bootstrap queries no
  -- longer using DISTINCT, this guards that the provider row is still returned once (the IN subquery is a semi-join
  -- and must not fan bar out to two rows).
  ('test-module-foo-1.0.0', 'test-bar-interface-2', '1.0', 'REQUIRES', null),
  ('test-module-bar-1.0.0', 'test-bar-interface-2', '1.0', 'PROVIDES', '{
    "id": "test-bar-interface-2", "version": "1.0",
    "handlers": [ {"methods": ["GET"], "pathPattern": "/bar2"} ]
  }'),
  -- an interface that nobody requires: it must never show up in the egress bootstrap
  ('test-module-bar-1.0.0', 'test-bar-unrelated-interface', '1.0', 'PROVIDES', '{
    "id": "test-bar-unrelated-interface", "version": "1.0",
    "handlers": [ {"methods": ["GET"], "pathPattern": "/unrelated"} ]
  }');
