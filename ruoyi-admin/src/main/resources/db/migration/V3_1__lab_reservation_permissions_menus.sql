create temporary table lab_v3_1_seed_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v3_1_seed_guard (guard_key) values ('X');

insert into lab_v3_1_seed_guard (guard_key)
select 'X'
where exists (
    select 1 from sys_menu
    where menu_id in (2300, 2301, 2302, 2310, 2311, 2320, 2321)
       or perms in ('lab:reservation:mine', 'lab:reservation:list',
                    'lab:reservation:apply', 'lab:reservation:cancel',
                    'lab:reservation:approve', 'lab:reservation:reject')
)
or exists (
    select 1 from sys_role_menu
    where menu_id in (2300, 2301, 2302, 2310, 2311, 2320, 2321)
)
or (select count(distinct role_key) from sys_role
    where role_key in ('lab_student', 'lab_manager')
      and status = '0' and del_flag = '0') &lt;&gt; 2;

insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
values
    (2300, '预约管理', 2000, 2, 'reservations', null, null, '',
     1, 0, 'M', '0', '0', '', 'date', 'admin', now(3), '', null, 'M3预约并发目录'),
    (2301, '我的预约', 2300, 1, 'mine', 'lab/reservation/index', 'mode=mine',
     'MyLabReservations', 1, 0, 'C', '0', '0', 'lab:reservation:mine', 'list',
     'admin', now(3), '', null, ''),
    (2302, '预约审批', 2300, 2, 'approval', 'lab/reservation/index', 'mode=approval',
     'LabReservationApproval', 1, 0, 'C', '0', '0', 'lab:reservation:list', 'audit',
     'admin', now(3), '', null, ''),
    (2310, '预约申请', 2301, 1, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:reservation:apply', '#', 'admin', now(3), '', null, ''),
    (2311, '预约取消', 2301, 2, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:reservation:cancel', '#', 'admin', now(3), '', null, ''),
    (2320, '预约批准', 2302, 1, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:reservation:approve', '#', 'admin', now(3), '', null, ''),
    (2321, '预约驳回', 2302, 2, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:reservation:reject', '#', 'admin', now(3), '', null, '');

insert into sys_role_menu (role_id, menu_id)
values
    ((select role_id from sys_role where role_key = 'lab_student'), 2300),
    ((select role_id from sys_role where role_key = 'lab_student'), 2301),
    ((select role_id from sys_role where role_key = 'lab_student'), 2310),
    ((select role_id from sys_role where role_key = 'lab_student'), 2311),
    ((select role_id from sys_role where role_key = 'lab_manager'), 2300),
    ((select role_id from sys_role where role_key = 'lab_manager'), 2302),
    ((select role_id from sys_role where role_key = 'lab_manager'), 2320),
    ((select role_id from sys_role where role_key = 'lab_manager'), 2321);
