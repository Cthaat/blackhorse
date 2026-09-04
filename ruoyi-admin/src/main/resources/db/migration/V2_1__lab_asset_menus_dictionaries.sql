-- Fail closed before the first persistent write. The temporary sentinel follows
-- the V1_2 guard pattern and disappears with the Flyway session on failure.
create temporary table lab_v2_1_seed_guard
(
  guard_key char(1) not null,
  primary key (guard_key)
) engine=memory;

insert into lab_v2_1_seed_guard (guard_key)
values ('X');

-- Reject ownership of any fixed menu identity, route identity, permission, or
-- role-menu row. Generic button paths such as '#' are intentionally excluded.
insert into lab_v2_1_seed_guard (guard_key)
select 'X'
 where exists
       (select 1
          from sys_menu
         where menu_id in
               (2200, 2201, 2202, 2203, 2204,
                2210, 2211, 2212, 2213,
                2220, 2221, 2222, 2223, 2224, 2225,
                2230, 2231, 2232, 2233)
            or (menu_type in ('M', 'C')
                and (path in
                     ('assets', 'laboratories', 'devices', 'qualifications',
                      'my-qualifications', 'LabLaboratories', 'LabDevices',
                      'LabQualifications', 'MyLabQualifications')
                     or route_name in
                        ('assets', 'laboratories', 'devices', 'qualifications',
                         'my-qualifications', 'LabLaboratories', 'LabDevices',
                         'LabQualifications', 'MyLabQualifications')))
            or perms in
               ('lab:laboratory:list', 'lab:laboratory:query',
                'lab:laboratory:add', 'lab:laboratory:edit',
                'lab:laboratory:status', 'lab:device:list',
                'lab:device:query', 'lab:device:add', 'lab:device:edit',
                'lab:device:status', 'lab:qualification:list',
                'lab:qualification:query', 'lab:qualification:add',
                'lab:qualification:edit', 'lab:qualification:revoke',
                'lab:qualification:mine', 'lab:attachment:manage',
                'lab:attachment:read'))
    or exists
       (select 1
          from sys_role_menu
         where menu_id in
               (2200, 2201, 2202, 2203, 2204,
                2210, 2211, 2212, 2213,
                2220, 2221, 2222, 2223, 2224, 2225,
                2230, 2231, 2232, 2233));

-- Dictionary type IDs 200..203 and data IDs 2000..2012 are reserved for M2.
-- Reject both numeric-ID collisions and semantic dict_type ownership.
insert into lab_v2_1_seed_guard (guard_key)
select 'X'
 where exists
       (select 1
          from sys_dict_type
         where dict_id in (200, 201, 202, 203)
            or dict_type in
               ('lab_laboratory_status', 'lab_device_status', 'lab_risk_level',
                'lab_qualification_scope_type'))
    or exists
       (select 1
          from sys_dict_data
         where dict_code between 2000 and 2012
            or dict_type in
               ('lab_laboratory_status', 'lab_device_status', 'lab_risk_level',
                'lab_qualification_scope_type'));

-- The parent menu and every role involved in this permission slice must retain
-- the active V1_2 identity. Count checks also reject duplicate role_key rows.
insert into lab_v2_1_seed_guard (guard_key)
select 'X'
 where if((select count(*)
             from sys_menu
            where menu_id = 2000
              and menu_name = '实验室管理'
              and parent_id = 0
              and path = 'lab'
              and component is null
              and menu_type = 'M'
              and status = '0') = 1, 0, 1)
       + if((select count(*)
               from sys_role
              where role_key in
                    ('lab_student', 'lab_manager', 'lab_safety_officer',
                     'lab_system_admin')) = 4, 0, 1)
       + if((select count(*)
               from sys_role
              where role_key in
                    ('lab_student', 'lab_manager', 'lab_safety_officer',
                     'lab_system_admin')
                and status = '0'
                and del_flag = '0') = 4, 0, 1)
       + if((select count(distinct role_key)
               from sys_role
              where role_key in
                    ('lab_student', 'lab_manager', 'lab_safety_officer',
                     'lab_system_admin')) = 4, 0, 1)
       + if((select count(*)
               from sys_role_menu role_menu
               join sys_role role_seed
                 on role_seed.role_id = role_menu.role_id
              where role_menu.menu_id = 2000
                and role_seed.role_key in
                    ('lab_student', 'lab_manager', 'lab_safety_officer',
                     'lab_system_admin')) = 4, 0, 1) > 0;

-- Persistent seed starts only after every guard above has succeeded.
insert into sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
   create_time, update_by, update_time, remark)
values
  (2200, '实验室资产', 2000, 1, 'assets', null, null, '',
   1, 0, 'M', '0', '0', '', 'build', 'admin', now(3), '', null,
   'M2资产资格目录'),
  (2201, '实验室管理', 2200, 1, 'laboratories', 'lab/laboratory/index', null,
   'LabLaboratories', 1, 0, 'C', '0', '0', 'lab:laboratory:list',
   'office-building', 'admin', now(3), '', null, ''),
  (2202, '设备管理', 2200, 2, 'devices', 'lab/device/index', null,
   'LabDevices', 1, 0, 'C', '0', '0', 'lab:device:list', 'monitor',
   'admin', now(3), '', null, ''),
  (2203, '资格管理', 2200, 3, 'qualifications', 'lab/qualification/index', null,
   'LabQualifications', 1, 0, 'C', '0', '0', 'lab:qualification:list',
   'education', 'admin', now(3), '', null, ''),
  (2204, '我的资格', 2200, 4, 'my-qualifications', 'lab/qualification/mine', null,
   'MyLabQualifications', 1, 0, 'C', '0', '0', 'lab:qualification:mine',
   'user', 'admin', now(3), '', null, '');

insert into sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
   create_time, update_by, update_time, remark)
values
  (2210, '实验室新增', 2201, 1, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:laboratory:add', '#', 'admin', now(3), '', null, ''),
  (2211, '实验室修改', 2201, 2, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:laboratory:edit', '#', 'admin', now(3), '', null, ''),
  (2212, '实验室状态', 2201, 3, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:laboratory:status', '#', 'admin', now(3), '', null, ''),
  (2213, '实验室详情', 2201, 4, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:laboratory:query', '#', 'admin', now(3), '', null, ''),
  (2220, '设备新增', 2202, 1, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:device:add', '#', 'admin', now(3), '', null, ''),
  (2221, '设备修改', 2202, 2, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:device:edit', '#', 'admin', now(3), '', null, ''),
  (2222, '设备状态', 2202, 3, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:device:status', '#', 'admin', now(3), '', null, ''),
  (2223, '附件管理', 2202, 4, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:attachment:manage', '#', 'admin', now(3), '', null, ''),
  (2224, '设备详情', 2202, 5, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:device:query', '#', 'admin', now(3), '', null, ''),
  (2225, '附件读取', 2202, 6, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:attachment:read', '#', 'admin', now(3), '', null, ''),
  (2230, '资格新增', 2203, 1, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:qualification:add', '#', 'admin', now(3), '', null, ''),
  (2231, '资格修改', 2203, 2, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:qualification:edit', '#', 'admin', now(3), '', null, ''),
  (2232, '资格撤销', 2203, 3, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:qualification:revoke', '#', 'admin', now(3), '', null, ''),
  (2233, '资格详情', 2203, 4, '#', '', null, '',
   1, 0, 'F', '0', '0', 'lab:qualification:query', '#', 'admin', now(3), '', null, '');

insert into sys_dict_type
  (dict_id, dict_name, dict_type, status, create_by, create_time, update_by,
   update_time, remark)
values
  (200, '实验室状态', 'lab_laboratory_status', '0', 'admin', now(3), '', null,
   '实验室启用状态'),
  (201, '设备状态', 'lab_device_status', '0', 'admin', now(3), '', null,
   '设备生命周期状态'),
  (202, '设备风险等级', 'lab_risk_level', '0', 'admin', now(3), '', null,
   '设备风险分级'),
  (203, '资格范围类型', 'lab_qualification_scope_type', '0', 'admin', now(3), '',
   null, '资格适用对象类型');

insert into sys_dict_data
  (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class,
   list_class, is_default, status, create_by, create_time, update_by,
   update_time, remark)
values
  (2000, 1, '启用', 'ENABLED', 'lab_laboratory_status', '', 'success', 'Y', '0',
   'admin', now(3), '', null, ''),
  (2001, 2, '停用', 'DISABLED', 'lab_laboratory_status', '', 'danger', 'N', '0',
   'admin', now(3), '', null, ''),
  (2002, 1, '可用', 'AVAILABLE', 'lab_device_status', '', 'success', 'Y', '0',
   'admin', now(3), '', null, ''),
  (2003, 2, '使用中', 'IN_USE', 'lab_device_status', '', 'primary', 'N', '0',
   'admin', now(3), '', null, ''),
  (2004, 3, '故障', 'FAULT', 'lab_device_status', '', 'danger', 'N', '0',
   'admin', now(3), '', null, ''),
  (2005, 4, '维护中', 'MAINTENANCE', 'lab_device_status', '', 'warning', 'N', '0',
   'admin', now(3), '', null, ''),
  (2006, 5, '停用', 'DISABLED', 'lab_device_status', '', 'info', 'N', '0',
   'admin', now(3), '', null, ''),
  (2007, 1, '低风险', 'LOW', 'lab_risk_level', '', 'success', 'Y', '0',
   'admin', now(3), '', null, ''),
  (2008, 2, '中风险', 'MEDIUM', 'lab_risk_level', '', 'warning', 'N', '0',
   'admin', now(3), '', null, ''),
  (2009, 3, '高风险', 'HIGH', 'lab_risk_level', '', 'danger', 'N', '0',
   'admin', now(3), '', null, ''),
  (2010, 4, '重大风险', 'MAJOR', 'lab_risk_level', '', 'danger', 'N', '0',
   'admin', now(3), '', null, ''),
  (2011, 1, '实验室', 'LABORATORY', 'lab_qualification_scope_type', '', 'primary',
   'Y', '0', 'admin', now(3), '', null, ''),
  (2012, 2, '设备类别', 'DEVICE_CATEGORY', 'lab_qualification_scope_type', '',
   'info', 'N', '0', 'admin', now(3), '', null, '');

-- Role grants use role_key exclusively; role IDs are deliberately not coupled
-- to this migration. lab_system_admin receives no new business permission.
insert into sys_role_menu (role_id, menu_id)
values
  ((select role_id from sys_role where role_key = 'lab_student'), 2200),
  ((select role_id from sys_role where role_key = 'lab_student'), 2202),
  ((select role_id from sys_role where role_key = 'lab_student'), 2204),
  ((select role_id from sys_role where role_key = 'lab_student'), 2224),
  ((select role_id from sys_role where role_key = 'lab_student'), 2225),

  ((select role_id from sys_role where role_key = 'lab_manager'), 2200),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2201),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2202),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2203),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2210),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2211),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2212),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2213),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2220),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2221),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2222),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2223),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2224),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2225),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2230),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2231),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2232),
  ((select role_id from sys_role where role_key = 'lab_manager'), 2233),

  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2200),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2201),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2202),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2203),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2213),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2224),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2225),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2230),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2231),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2232),
  ((select role_id from sys_role where role_key = 'lab_safety_officer'), 2233);
