create temporary table lab_v6_1_seed_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_1_seed_guard (guard_key) values ('X');

insert into lab_v6_1_seed_guard (guard_key)
select 'X'
where exists (
    select 1 from sys_menu
    where menu_id in (4600, 4610, 4611)
       or perms in ('lab:dashboard:view', 'lab:notification:list', 'lab:notification:read')
)
or exists (
    select 1 from sys_job
    where job_id between 6000 and 6005
       or (job_group = 'LAB_SYSTEM' and invoke_target like 'labLifecycleJob.%')
)
or (select count(distinct role_key) from sys_role
    where role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                       'lab_repair_worker', 'lab_system_admin')
      and status = '0' and del_flag = '0') <> 5;

insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
values
    (4600, '实验室工作台', 2000, 6, 'dashboard', 'lab/dashboard/index', null,
     'LabDashboard', 1, 0, 'C', '0', '0', 'lab:dashboard:view', 'dashboard',
     'admin', now(3), '', null, 'V6角色工作台'),
    (4610, '消息中心', 2000, 7, 'notifications', 'lab/notification/index', null,
     'LabNotifications', 1, 0, 'C', '0', '0', 'lab:notification:list', 'message',
     'admin', now(3), '', null, 'V6站内消息'),
    (4611, '消息已读', 4610, 1, '#', '', null, '',
     1, 0, 'F', '0', '0', 'lab:notification:read', '#',
     'admin', now(3), '', null, '');

insert into sys_role_menu (role_id, menu_id)
select role_seed.role_id, menu_seed.menu_id
from sys_role role_seed
join sys_menu menu_seed on menu_seed.menu_id in (4600, 4610, 4611)
where role_seed.role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                             'lab_repair_worker', 'lab_system_admin');

insert into sys_job
    (job_id, job_name, job_group, invoke_target, cron_expression,
     misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
values
    (6000, '实验室预约过期', 'LAB_SYSTEM', 'labLifecycleJob.expirePendingReservations()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, 'PENDING到时转EXPIRED'),
    (6001, '实验室预约爽约', 'LAB_SYSTEM', 'labLifecycleJob.markNoShowReservations()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, 'APPROVED超宽限转NO_SHOW'),
    (6002, '实验室巡检生成', 'LAB_SYSTEM', 'labLifecycleJob.generateInspectionTasks()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, '按受控周期生成巡检任务'),
    (6003, '实验室巡检超期', 'LAB_SYSTEM', 'labLifecycleJob.markInspectionOverdue()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, '标记巡检超期'),
    (6004, '实验室整改超期', 'LAB_SYSTEM', 'labLifecycleJob.markHazardOverdue()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, '标记整改超期'),
    (6005, '实验室通知补偿', 'LAB_SYSTEM', 'labLifecycleJob.compensateNotifications()',
     '0 * * * * ?', '2', '1', '0', 'admin', now(3), '', null, '重试FAILED并对账状态历史');
