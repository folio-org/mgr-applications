create database am_it;

create user app_manager_admin with password 'folio123';
alter database am_it owner to app_manager_admin;

create database kong_it;

create user kong_admin with password 'kong123';
alter database kong_it owner to kong_admin;
