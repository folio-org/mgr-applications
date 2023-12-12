create database am_it;

create user app_manager_admin with password 'folio123';
grant connect on database am_it to app_manager_admin;
grant all privileges on database am_it to app_manager_admin;

create database kong_it;

create user kong_admin with password 'kong123';
grant connect on database kong_it to kong_admin;
grant all privileges on database kong_it to kong_admin;
