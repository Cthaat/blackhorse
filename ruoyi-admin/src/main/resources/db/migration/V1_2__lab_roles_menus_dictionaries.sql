-- Fail closed before the first persistent write. CREATE TEMPORARY TABLE is session-scoped
-- and does not leave a durable object if a guard rejects a pre-existing identity.
create temporary table lab_v1_2_seed_guard
(
  guard_key char(1) not null,
  primary key (guard_key)
) engine=memory;

insert into lab_v1_2_seed_guard (guard_key)
values ('X');

-- A violation attempts to insert the sentinel a second time. The primary-key
-- collision is enforced by every supported MySQL 8 release and aborts the
-- migration before any persistent table is changed.
insert into lab_v1_2_seed_guard (guard_key)
select 'X'
 where (select count(*)
          from sys_role
         where role_id in (100, 101, 102, 103, 104)
            or role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                            'lab_repair_worker', 'lab_system_admin'))
       + (select count(*)
            from sys_menu
           where menu_id = 2000
              or path = 'lab')
       + (select count(*)
            from sys_user
           where user_id = 9000
              or user_name = '__lab_system_operator__')
       + (select count(*)
            from sys_config
           where config_id = 100
              or config_key = 'lab.system.operator-user-id')
       + (select count(*)
            from sys_role_menu
           where role_id in (100, 101, 102, 103, 104)
              or menu_id = 2000)
       + (select count(*)
            from sys_role_dept
           where role_id in (100, 101, 102, 103, 104))
       + (select count(*)
            from sys_user_role
           where user_id = 9000
              or role_id in (100, 101, 102, 103, 104))
       + (select count(*)
            from sys_user_post
           where user_id = 9000) > 0;

-- These are the only V1_0 system menus granted to lab_system_admin. Capturing their
-- routing and permission identity here prevents a drifted baseline from granting an
-- unrelated menu merely because it reused one of the fixed IDs.
create temporary table lab_v1_2_expected_system_menu
(
  menu_id    bigint       not null primary key,
  menu_name  varchar(50)  not null,
  parent_id  bigint       not null,
  path       varchar(200) not null,
  component  varchar(255) null,
  menu_type  char(1)      not null,
  perms      varchar(100) not null
) engine=memory;

insert into lab_v1_2_expected_system_menu
  (menu_id, menu_name, parent_id, path, component, menu_type, perms)
values
  (1,    '系统管理', 0,   'system',     null,                     'M', ''),
  (2,    '系统监控', 0,   'monitor',    null,                     'M', ''),
  (100,  '用户管理', 1,   'user',       'system/user/index',      'C', 'system:user:list'),
  (101,  '角色管理', 1,   'role',       'system/role/index',      'C', 'system:role:list'),
  (102,  '菜单管理', 1,   'menu',       'system/menu/index',      'C', 'system:menu:list'),
  (103,  '部门管理', 1,   'dept',       'system/dept/index',      'C', 'system:dept:list'),
  (105,  '字典管理', 1,   'dict',       'system/dict/index',      'C', 'system:dict:list'),
  (106,  '参数设置', 1,   'config',     'system/config/index',    'C', 'system:config:list'),
  (108,  '日志管理', 1,   'log',        '',                       'M', ''),
  (110,  '定时任务', 2,   'job',        'monitor/job/index',      'C', 'monitor:job:list'),
  (500,  '操作日志', 108, 'operlog',    'monitor/operlog/index',  'C', 'monitor:operlog:list'),
  (501,  '登录日志', 108, 'logininfor', 'monitor/logininfor/index','C', 'monitor:logininfor:list'),
  (1000, '用户查询', 100, '',           '',                       'F', 'system:user:query'),
  (1004, '用户导出', 100, '',           '',                       'F', 'system:user:export'),
  (1007, '角色查询', 101, '',           '',                       'F', 'system:role:query'),
  (1011, '角色导出', 101, '',           '',                       'F', 'system:role:export'),
  (1012, '菜单查询', 102, '',           '',                       'F', 'system:menu:query'),
  (1016, '部门查询', 103, '',           '',                       'F', 'system:dept:query'),
  (1017, '部门新增', 103, '',           '',                       'F', 'system:dept:add'),
  (1018, '部门修改', 103, '',           '',                       'F', 'system:dept:edit'),
  (1019, '部门删除', 103, '',           '',                       'F', 'system:dept:remove'),
  (1025, '字典查询', 105, '#',          '',                       'F', 'system:dict:query'),
  (1026, '字典新增', 105, '#',          '',                       'F', 'system:dict:add'),
  (1027, '字典修改', 105, '#',          '',                       'F', 'system:dict:edit'),
  (1028, '字典删除', 105, '#',          '',                       'F', 'system:dict:remove'),
  (1029, '字典导出', 105, '#',          '',                       'F', 'system:dict:export'),
  (1030, '参数查询', 106, '#',          '',                       'F', 'system:config:query'),
  (1031, '参数新增', 106, '#',          '',                       'F', 'system:config:add'),
  (1032, '参数修改', 106, '#',          '',                       'F', 'system:config:edit'),
  (1033, '参数删除', 106, '#',          '',                       'F', 'system:config:remove'),
  (1034, '参数导出', 106, '#',          '',                       'F', 'system:config:export'),
  (1039, '操作查询', 500, '#',          '',                       'F', 'monitor:operlog:query'),
  (1040, '操作删除', 500, '#',          '',                       'F', 'monitor:operlog:remove'),
  (1041, '日志导出', 500, '#',          '',                       'F', 'monitor:operlog:export'),
  (1042, '登录查询', 501, '#',          '',                       'F', 'monitor:logininfor:query'),
  (1043, '登录删除', 501, '#',          '',                       'F', 'monitor:logininfor:remove'),
  (1044, '日志导出', 501, '#',          '',                       'F', 'monitor:logininfor:export'),
  (1045, '账户解锁', 501, '#',          '',                       'F', 'monitor:logininfor:unlock'),
  (1049, '任务查询', 110, '#',          '',                       'F', 'monitor:job:query'),
  (1050, '任务新增', 110, '#',          '',                       'F', 'monitor:job:add'),
  (1051, '任务修改', 110, '#',          '',                       'F', 'monitor:job:edit'),
  (1052, '任务删除', 110, '#',          '',                       'F', 'monitor:job:remove'),
  (1053, '状态修改', 110, '#',          '',                       'F', 'monitor:job:changeStatus'),
  (1054, '任务导出', 110, '#',          '',                       'F', 'monitor:job:export');

insert into lab_v1_2_seed_guard (guard_key)
select 'X'
 where exists
       (select 1
          from lab_v1_2_expected_system_menu expected
          left join sys_menu actual
            on actual.menu_id = expected.menu_id
           and actual.menu_name = expected.menu_name
           and actual.parent_id = expected.parent_id
           and actual.path = expected.path
           and actual.component <=> expected.component
           and actual.menu_type = expected.menu_type
           and actual.perms <=> expected.perms
           and actual.is_frame = 1
           and actual.is_cache = 0
           and actual.visible = '0'
           and actual.status = '0'
         where actual.menu_id is null);

insert into lab_v1_2_seed_guard (guard_key)
select 'X'
 where if((select count(*)
             from sys_dept
            where dept_id = 103
              and dept_name = '研发部门'
              and status = '0'
              and del_flag = '0') = 1, 0, 1)
       + if((select count(*)
               from sys_dict_type
              where (dict_type = 'sys_normal_disable' and dict_name = '系统开关' and status = '0')
                 or (dict_type = 'sys_yes_no' and dict_name = '系统是否' and status = '0')) = 2, 0, 1)
       + if((select count(*)
               from sys_dict_data
              where dict_type = 'sys_normal_disable' and dict_label = '正常' and dict_value = '0'
                and status = '0') = 1, 0, 1)
       + if((select count(*)
               from sys_dict_data
              where dict_type = 'sys_normal_disable' and dict_label = '停用' and dict_value = '1'
                and status = '0') = 1, 0, 1)
       + if((select count(*)
               from sys_dict_data
              where dict_type = 'sys_yes_no' and dict_label = '是' and dict_value = 'Y'
                and status = '0') = 1, 0, 1)
       + if((select count(*)
               from sys_dict_data
              where dict_type = 'sys_yes_no' and dict_label = '否' and dict_value = 'N'
                and status = '0') = 1, 0, 1) > 0;

-- Persistent seed starts only after every guard above has succeeded.
insert into sys_role
  (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
   dept_check_strictly, status, del_flag, create_by, create_time, update_by,
   update_time, remark)
values
  (100, '学生',         'lab_student',        100, '5', 1, 1, '0', '0', 'system', now(3), '', null, '实验室管理演示角色'),
  (101, '实验室管理员', 'lab_manager',        101, '3', 1, 1, '0', '0', 'system', now(3), '', null, '实验室管理演示角色'),
  (102, '安全员',       'lab_safety_officer', 102, '3', 1, 1, '0', '0', 'system', now(3), '', null, '实验室管理演示角色'),
  (103, '维修人员',     'lab_repair_worker',  103, '5', 1, 1, '0', '0', 'system', now(3), '', null, '实验室管理演示角色'),
  (104, '系统管理员',   'lab_system_admin',   104, '1', 1, 1, '0', '0', 'system', now(3), '', null, '实验室管理演示角色');

insert into sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
   create_time, update_by, update_time, remark)
values
  (2000, '实验室管理', 0, 4, 'lab', null, '', '', 1, 0, 'M', '0', '0', '',
   'education', 'system', now(3), '', null, '实验室业务根目录；子菜单由后续迁移追加');

insert into sys_user
  (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber,
   sex, avatar, password, status, del_flag, login_ip, login_date,
   pwd_update_date, create_by, create_time, update_by, update_time, remark)
values
  (9000, null, '__lab_system_operator__', '实验室系统任务', '00', '', '',
   '2', '', '!NO_LOGIN!', '1', '0', '', null,
   null, 'system', now(3), '', null, '禁登录；仅作为自动领域命令的操作人');

insert into sys_config
  (config_id, config_name, config_key, config_value, config_type,
   create_by, create_time, update_by, update_time, remark)
values
  (100, '实验室系统操作账号', 'lab.system.operator-user-id', '9000', 'Y',
   'system', now(3), '', null, '状态历史中自动命令的 operator_id');

insert into sys_role_menu (role_id, menu_id)
values
  (100, 2000),
  (101, 2000),
  (102, 2000),
  (103, 2000),
  (104, 2000),
  (104, 1),
  (104, 2),
  (104, 100),
  (104, 101),
  (104, 102),
  (104, 103),
  (104, 105),
  (104, 106),
  (104, 108),
  (104, 110),
  (104, 500),
  (104, 501),
  (104, 1000),
  (104, 1004),
  (104, 1007),
  (104, 1011),
  (104, 1012),
  (104, 1016),
  (104, 1017),
  (104, 1018),
  (104, 1019),
  (104, 1025),
  (104, 1026),
  (104, 1027),
  (104, 1028),
  (104, 1029),
  (104, 1030),
  (104, 1031),
  (104, 1032),
  (104, 1033),
  (104, 1034),
  (104, 1039),
  (104, 1040),
  (104, 1041),
  (104, 1042),
  (104, 1043),
  (104, 1044),
  (104, 1045),
  (104, 1049),
  (104, 1050),
  (104, 1051),
  (104, 1052),
  (104, 1053),
  (104, 1054);
