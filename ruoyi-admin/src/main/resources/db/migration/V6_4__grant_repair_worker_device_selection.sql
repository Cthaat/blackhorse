-- The repair report form obtains its device choices from /lab/devices/list.
-- Grant the existing list permission without adding the asset menu parent, so
-- repair workers receive only the supporting API capability they require.
create temporary table lab_v6_4_permission_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_4_permission_guard (guard_key) values ('X');

insert into lab_v6_4_permission_guard (guard_key)
select 'X'
where (select count(*)
         from sys_role
        where role_key = 'lab_repair_worker'
          and status = '0'
          and del_flag = '0') <> 1
   or (select count(*)
         from sys_menu
        where menu_id = 2202
          and perms = 'lab:device:list'
          and status = '0') <> 1
   or exists (
        select 1
          from sys_role_menu role_menu
          join sys_role role_seed on role_seed.role_id = role_menu.role_id
         where role_seed.role_key = 'lab_repair_worker'
           and role_menu.menu_id = 2202
   );

insert into sys_role_menu (role_id, menu_id)
values
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 2202);
