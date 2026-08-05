create database am_it;

create user app_manager_admin with password 'folio123';
alter database am_it owner to app_manager_admin;
