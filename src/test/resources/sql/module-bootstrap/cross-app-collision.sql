insert into application(id, name, version, application_descriptor)
values
  ('app-platform-minimal-2.0.53', 'app-platform-minimal', '2.0.53', '{
    "id": "app-platform-minimal-2.0.53",
    "name": "app-platform-minimal",
    "version": "2.0.53",
    "modules": [
      {"id": "mod-users-keycloak-3.0.13", "name": "mod-users-keycloak", "version": "3.0.13"},
      {"id": "mod-users-19.5.4", "name": "mod-users", "version": "19.5.4"}
    ]
  }'),
  ('app-platform-complete-1.2.0', 'app-platform-complete', '1.2.0', '{
    "id": "app-platform-complete-1.2.0",
    "name": "app-platform-complete",
    "version": "1.2.0",
    "modules": [
      {"id": "mod-users-19.6.0", "name": "mod-users", "version": "19.6.0"}
    ]
  }');

INSERT INTO module(id, name, version, discovery_url, descriptor, type)
VALUES
  ('mod-users-keycloak-3.0.13', 'mod-users-keycloak', '3.0.13',
   'http://mod-users-keycloak-3-0-13',
   '{"id":"mod-users-keycloak-3.0.13","requires":[{"id":"users","version":"19.0"}]}',
   'BACKEND'),
  ('mod-users-19.5.4', 'mod-users', '19.5.4',
   'http://mod-users-19-5-4',
   '{"id":"mod-users-19.5.4","provides":[{"id":"users","version":"19.5"}]}',
   'BACKEND'),
  ('mod-users-19.6.0', 'mod-users', '19.6.0',
   'http://mod-users-19-6-0',
   '{"id":"mod-users-19.6.0","provides":[{"id":"users","version":"19.6"}]}',
   'BACKEND');

INSERT INTO application_module(application_id, module_id)
VALUES
  ('app-platform-minimal-2.0.53', 'mod-users-keycloak-3.0.13'),
  ('app-platform-minimal-2.0.53', 'mod-users-19.5.4'),
  ('app-platform-complete-1.2.0', 'mod-users-19.6.0');

insert into module_interface_reference(module_id, id, version, type) VALUES
  ('mod-users-keycloak-3.0.13', 'users', '19.0', 'REQUIRES'),
  ('mod-users-19.5.4', 'users', '19.5', 'PROVIDES'),
  ('mod-users-19.6.0', 'users', '19.6', 'PROVIDES');
