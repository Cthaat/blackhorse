-- Close role grants required by attachment-backed repair and rectification flows.
-- Object-level authorization remains authoritative; these rows only expose the
-- corresponding endpoints and buttons to actors already participating in them.
create temporary table lab_v6_2_permission_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_2_permission_guard (guard_key) values ('X');

insert into lab_v6_2_permission_guard (guard_key)
select 'X'
where (select count(distinct role_key)
         from sys_role
        where role_key in ('lab_student', 'lab_safety_officer', 'lab_repair_worker')
          and status = '0' and del_flag = '0') <> 3
   or (select count(*) from sys_menu
        where (menu_id = 2223 and perms = 'lab:attachment:manage')
           or (menu_id = 2225 and perms = 'lab:attachment:read')
           or (menu_id = 4312 and perms = 'lab:repair:report')
           or (menu_id = 4400 and menu_type = 'M' and path = 'safety')
           or (menu_id = 4403 and perms = 'lab:hazard:list')
           or (menu_id = 4429 and perms = 'lab:hazard:rectify')) <> 6
   or exists (
        select 1
          from sys_role_menu role_menu
          join sys_role role_seed on role_seed.role_id = role_menu.role_id
         where (role_seed.role_key = 'lab_student'
                and role_menu.menu_id in (2223, 4400, 4403, 4429))
            or (role_seed.role_key = 'lab_safety_officer'
                and role_menu.menu_id = 2223)
            or (role_seed.role_key = 'lab_repair_worker'
                and role_menu.menu_id in (2223, 2225, 4312))
   );

insert into sys_role_menu (role_id, menu_id)
values
    ((select role_id from sys_role where role_key = 'lab_student'), 2223),
    ((select role_id from sys_role where role_key = 'lab_student'), 4400),
    ((select role_id from sys_role where role_key = 'lab_student'), 4403),
    ((select role_id from sys_role where role_key = 'lab_student'), 4429),
    ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2223),
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 2223),
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 2225),
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 4312);
