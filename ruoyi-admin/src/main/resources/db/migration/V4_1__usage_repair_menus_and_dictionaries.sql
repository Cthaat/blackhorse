create temporary table lab_v4_1_seed_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v4_1_seed_guard (guard_key) values ('X');

insert into lab_v4_1_seed_guard (guard_key)
select 'X'
where exists (
    select 1 from sys_menu
    where menu_id in (4300, 4301, 4302, 4303, 4310, 4311, 4312, 4313, 4314, 4315)
       or perms in ('lab:usage:list', 'lab:usage:query', 'lab:usage:checkout',
                    'lab:usage:return', 'lab:repair:list', 'lab:repair:query',
                    'lab:repair:report', 'lab:repair:assign',
                    'lab:repair:process', 'lab:repair:accept')
)
or exists (
    select 1 from sys_role_menu
    where menu_id in (4300, 4301, 4302, 4303, 4310, 4311, 4312, 4313, 4314, 4315)
)
or exists (
    select 1 from sys_dict_type
    where dict_id in (204, 205)
       or dict_type in ('lab_return_condition', 'lab_repair_status')
)
or exists (
    select 1 from sys_dict_data
    where dict_code between 2020 and 2027
       or dict_type in ('lab_return_condition', 'lab_repair_status')
)
or (select count(distinct role_key) from sys_role
    where role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                       'lab_repair_worker')
      and status = '0' and del_flag = '0') <> 4;

insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
values
    (4300, '领用归还', 2000, 3, 'usage', 'lab/usage/index', null,
     'LabUsage', 1, 0, 'C', '0', '0', 'lab:usage:list', 'time-range',
     'admin', now(3), '', null, '实验室设备领用归还'),
    (4310, '维修工单', 2000, 4, 'repair', 'lab/repair/index', null,
     'LabRepair', 1, 0, 'C', '0', '0', 'lab:repair:list', 'tool',
     'admin', now(3), '', null, '实验室设备维修闭环'),
    (4301, '设备领用', 4300, 1, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:usage:checkout', '#', 'admin', now(3), '', null, ''),
    (4302, '设备归还', 4300, 2, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:usage:return', '#', 'admin', now(3), '', null, ''),
    (4303, '领用详情', 4300, 3, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:usage:query', '#', 'admin', now(3), '', null, ''),
    (4311, '维修查询', 4310, 1, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:repair:query', '#', 'admin', now(3), '', null, ''),
    (4312, '提交故障', 4310, 2, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:repair:report', '#', 'admin', now(3), '', null, ''),
    (4313, '维修分派', 4310, 3, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:repair:assign', '#', 'admin', now(3), '', null, ''),
    (4314, '维修处理', 4310, 4, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:repair:process', '#', 'admin', now(3), '', null, ''),
    (4315, '维修验收', 4310, 5, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:repair:accept', '#', 'admin', now(3), '', null, '');

insert into sys_dict_type
    (dict_id, dict_name, dict_type, status, create_by, create_time,
     update_by, update_time, remark)
values
    (204, '设备归还状态', 'lab_return_condition', '0', 'admin', now(3), '', null, ''),
    (205, '维修工单状态', 'lab_repair_status', '0', 'admin', now(3), '', null, '');

insert into sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class,
     list_class, is_default, status, create_by, create_time, update_by,
     update_time, remark)
values
    (2020, 1, '正常', 'NORMAL', 'lab_return_condition', '', 'success', 'Y', '0', 'admin', now(3), '', null, ''),
    (2021, 2, '损坏', 'DAMAGED', 'lab_return_condition', '', 'danger', 'N', '0', 'admin', now(3), '', null, ''),
    (2022, 3, '故障', 'FAULT', 'lab_return_condition', '', 'danger', 'N', '0', 'admin', now(3), '', null, ''),
    (2023, 1, '待分派', 'WAIT_ASSIGN', 'lab_repair_status', '', 'warning', 'Y', '0', 'admin', now(3), '', null, ''),
    (2024, 2, '待维修', 'WAIT_REPAIR', 'lab_repair_status', '', 'warning', 'N', '0', 'admin', now(3), '', null, ''),
    (2025, 3, '维修中', 'IN_PROGRESS', 'lab_repair_status', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (2026, 4, '待验收', 'WAIT_ACCEPTANCE', 'lab_repair_status', '', 'warning', 'N', '0', 'admin', now(3), '', null, ''),
    (2027, 5, '已关闭', 'CLOSED', 'lab_repair_status', '', 'success', 'N', '0', 'admin', now(3), '', null, '');

insert into sys_role_menu (role_id, menu_id)
values
    ((select role_id from sys_role where role_key = 'lab_student'), 4300),
    ((select role_id from sys_role where role_key = 'lab_student'), 4303),
    ((select role_id from sys_role where role_key = 'lab_student'), 4310),
    ((select role_id from sys_role where role_key = 'lab_student'), 4311),
    ((select role_id from sys_role where role_key = 'lab_student'), 4312),

    ((select role_id from sys_role where role_key = 'lab_manager'), 4300),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4301),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4302),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4303),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4310),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4311),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4312),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4313),
    ((select role_id from sys_role where role_key = 'lab_manager'), 4315),

    ((select role_id from sys_role where role_key = 'lab_safety_officer'), 4310),
    ((select role_id from sys_role where role_key = 'lab_safety_officer'), 4311),
    ((select role_id from sys_role where role_key = 'lab_safety_officer'), 4312),

    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 4310),
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 4311),
    ((select role_id from sys_role where role_key = 'lab_repair_worker'), 4314);
