-- Replace upstream demonstration identities with laboratory-project identities.
-- Exact guards ensure this migration never overwrites user-maintained records.
create temporary table lab_v6_8_expected_department
(
    dept_id bigint not null,
    old_name varchar(30) not null,
    new_name varchar(30) not null,
    primary key (dept_id)
) engine=memory;

insert into lab_v6_8_expected_department (dept_id, old_name, new_name)
values
    (100, '若依科技', '实验教学中心'),
    (101, '深圳总公司', '信息工程实验中心'),
    (102, '长沙分公司', '公共基础实验中心'),
    (103, '研发部门', '计算机实验教学部'),
    (104, '市场部门', '设备管理部'),
    (105, '测试部门', '安全管理部'),
    (106, '财务部门', '综合管理部'),
    (107, '运维部门', '运维保障部'),
    (108, '市场部门', '基础实验教学部'),
    (109, '财务部门', '公共设备管理部');

create temporary table lab_v6_8_expected_post
(
    post_id bigint not null,
    old_code varchar(64) not null,
    old_name varchar(50) not null,
    new_code varchar(64) not null,
    new_name varchar(50) not null,
    primary key (post_id)
) engine=memory;

insert into lab_v6_8_expected_post (post_id, old_code, old_name, new_code, new_name)
values
    (1, 'ceo', '董事长', 'center_director', '中心负责人'),
    (2, 'se', '项目经理', 'lab_supervisor', '实验室主管'),
    (3, 'hr', '人力资源', 'safety_officer', '安全管理专员'),
    (4, 'user', '普通员工', 'equipment_technician', '设备运维专员');

create temporary table lab_v6_8_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_8_guard (guard_key) values ('X');

insert into lab_v6_8_guard (guard_key)
select 'X'
where (select count(*)
         from lab_v6_8_expected_department expected
          inner join sys_dept actual
           on actual.dept_id = expected.dept_id
          and actual.dept_name = expected.old_name
          and actual.status = '0'
          and actual.del_flag = '0') <> 10
   or (select count(*)
         from lab_v6_8_expected_post expected
         inner join sys_post actual
           on actual.post_id = expected.post_id
          and actual.post_code = expected.old_code
          and actual.post_name = expected.old_name
          and actual.status = '0') <> 4
   or (select count(*) from sys_user
        where user_id = 1 and user_name = 'admin' and nick_name = '若依'
          and status = '0' and del_flag = '0') <> 1
   or (select count(*) from sys_user
        where user_id = 2 and user_name = 'ry' and nick_name = '若依'
          and status = '0' and del_flag = '0') <> 1
   or (select count(*) from sys_user
        where user_name = '__retired_legacy_user_2__') <> 0
   or (select count(*) from sys_role
        where role_id = 2 and role_key = 'common' and role_name = '普通角色'
          and role_sort = 2 and data_scope = '2'
          and menu_check_strictly = 1 and dept_check_strictly = 1
          and status = '0' and del_flag = '0'
          and create_by = 'admin' and update_by = '' and update_time is null
          and remark = '普通角色') <> 1
   or (select count(*) from sys_user_role where user_id = 2 and role_id = 2) <> 1
   or (select count(*) from sys_user_role where user_id = 2) <> 1
   or (select count(*) from sys_user_role where role_id = 2) <> 1
   or (select count(*) from sys_user_post where user_id = 2 and post_id = 2) <> 1
   or (select count(*) from sys_user_post where user_id = 2) <> 1
   or (select count(*) from sys_role_menu where role_id = 2) <> 85
   or exists (select 1 from sys_role_menu
        where role_id = 2
          and menu_id not in (1, 2, 3, 4, 500, 501)
          and menu_id not between 100 and 117
          and menu_id not between 1000 and 1060)
   or (select count(*) from sys_role_dept
        where role_id = 2 and dept_id in (100, 101, 105)) <> 3
   or (select count(*) from sys_role_dept where role_id = 2) <> 3;

update sys_dept department
inner join lab_v6_8_expected_department expected on expected.dept_id = department.dept_id
set department.dept_name = expected.new_name,
    department.leader = case when department.leader = '若依'
        then '实验教学中心' else department.leader end,
    department.phone = case when department.phone = '15888888888'
        then '' else department.phone end,
    department.email = case when department.email = 'ry@qq.com'
        then '' else department.email end,
    department.update_by = 'migration',
    department.update_time = sysdate();

update sys_post post
inner join lab_v6_8_expected_post expected on expected.post_id = post.post_id
set post.post_code = expected.new_code,
    post.post_name = expected.new_name,
    post.update_by = 'migration',
    post.update_time = sysdate();

update sys_user
set nick_name = '系统管理员',
    email = case when email = 'ry@163.com' then '' else email end,
    phonenumber = case when phonenumber = '15888888888' then '' else phonenumber end,
    update_by = 'migration',
    update_time = sysdate(),
    remark = case when remark = '管理员' then '平台内置管理员' else remark end
where user_id = 1 and user_name = 'admin' and nick_name = '若依';

delete from sys_user_post where user_id = 2;
delete from sys_user_role where user_id = 2;
update sys_user
set user_name = '__retired_legacy_user_2__',
    nick_name = '已停用历史账号',
    status = '1',
    del_flag = '2',
    update_by = 'migration',
    update_time = sysdate()
where user_id = 2 and user_name = 'ry' and nick_name = '若依';

delete from sys_role_menu where role_id = 2;
delete from sys_role_dept where role_id = 2;
delete from sys_role where role_id = 2 and role_key = 'common' and role_name = '普通角色';

-- Temporary tables are connection-scoped and disappear when Flyway closes it.
