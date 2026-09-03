create temporary table lab_v5_1_seed_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v5_1_seed_guard (guard_key) values ('X');

insert into lab_v5_1_seed_guard (guard_key)
select 'X'
where exists (
    select 1 from sys_menu
    where menu_id in (4400,4401,4402,4403,4410,4411,4412,4420,4428,4429,4430)
       or perms in ('lab:inspection:plan:list','lab:inspection:plan:add',
                    'lab:inspection:plan:edit','lab:inspection:plan:enable',
                    'lab:inspection:task:list','lab:inspection:task:execute',
                    'lab:hazard:list','lab:hazard:add','lab:hazard:rectify','lab:hazard:review')
)
or exists (
    select 1 from sys_dict_type
    where dict_id in (500,501,502,503)
       or dict_type in ('lab_inspection_frequency','lab_inspection_result',
                        'lab_hazard_severity','lab_hazard_status')
)
or exists (
    select 1 from sys_dict_data
    where dict_code between 5000 and 5014
       or dict_type in ('lab_inspection_frequency','lab_inspection_result',
                        'lab_hazard_severity','lab_hazard_status')
)
or (select count(distinct role_key) from sys_role
    where role_key in ('lab_manager','lab_safety_officer','lab_repair_worker')
      and status='0' and del_flag='0') <> 3;

insert into sys_menu
    (menu_id,menu_name,parent_id,order_num,path,component,query,route_name,
     is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,
     update_by,update_time,remark)
values
    (4400,'安全巡检',2000,5,'safety',null,null,'',1,0,'M','0','0','','shield','admin',now(3),'',null,'M5巡检隐患目录'),
    (4401,'巡检计划',4400,1,'inspection-plans','lab/inspection/plan/index',null,'LabInspectionPlans',1,0,'C','0','0','lab:inspection:plan:list','calendar','admin',now(3),'',null,''),
    (4402,'巡检任务',4400,2,'inspection-tasks','lab/inspection/task/index',null,'LabInspectionTasks',1,0,'C','0','0','lab:inspection:task:list','list','admin',now(3),'',null,''),
    (4403,'隐患整改',4400,3,'hazards','lab/hazard/index',null,'LabHazards',1,0,'C','0','0','lab:hazard:list','warning','admin',now(3),'',null,''),
    (4410,'计划新增',4401,1,'#','',null,'',1,0,'F','0','0','lab:inspection:plan:add','#','admin',now(3),'',null,''),
    (4411,'计划修改',4401,2,'#','',null,'',1,0,'F','0','0','lab:inspection:plan:edit','#','admin',now(3),'',null,''),
    (4412,'计划启停',4401,3,'#','',null,'',1,0,'F','0','0','lab:inspection:plan:enable','#','admin',now(3),'',null,''),
    (4420,'执行巡检',4402,1,'#','',null,'',1,0,'F','0','0','lab:inspection:task:execute','#','admin',now(3),'',null,''),
    (4428,'隐患复查',4403,4,'#','',null,'',1,0,'F','0','0','lab:hazard:review','#','admin',now(3),'',null,''),
    (4429,'隐患整改',4403,3,'#','',null,'',1,0,'F','0','0','lab:hazard:rectify','#','admin',now(3),'',null,''),
    (4430,'隐患登记',4403,2,'#','',null,'',1,0,'F','0','0','lab:hazard:add','#','admin',now(3),'',null,'');

insert into sys_dict_type
    (dict_id,dict_name,dict_type,status,create_by,create_time,update_by,update_time,remark)
values
    (500,'巡检频率','lab_inspection_frequency','0','admin',now(3),'',null,''),
    (501,'巡检结果','lab_inspection_result','0','admin',now(3),'',null,''),
    (502,'隐患等级','lab_hazard_severity','0','admin',now(3),'',null,''),
    (503,'隐患状态','lab_hazard_status','0','admin',now(3),'',null,'');

insert into sys_dict_data
    (dict_code,dict_sort,dict_label,dict_value,dict_type,css_class,list_class,
     is_default,status,create_by,create_time,update_by,update_time,remark)
values
    (5000,1,'每日','DAILY','lab_inspection_frequency','','primary','Y','0','admin',now(3),'',null,''),
    (5001,2,'每周','WEEKLY','lab_inspection_frequency','','primary','N','0','admin',now(3),'',null,''),
    (5002,3,'每月','MONTHLY','lab_inspection_frequency','','primary','N','0','admin',now(3),'',null,''),
    (5003,1,'合格','PASS','lab_inspection_result','','success','Y','0','admin',now(3),'',null,''),
    (5004,2,'不合格','FAIL','lab_inspection_result','','danger','N','0','admin',now(3),'',null,''),
    (5005,3,'不适用','NOT_APPLICABLE','lab_inspection_result','','info','N','0','admin',now(3),'',null,''),
    (5006,1,'低','LOW','lab_hazard_severity','','success','Y','0','admin',now(3),'',null,''),
    (5007,2,'中','MEDIUM','lab_hazard_severity','','warning','N','0','admin',now(3),'',null,''),
    (5008,3,'高','HIGH','lab_hazard_severity','','danger','N','0','admin',now(3),'',null,''),
    (5009,4,'重大','MAJOR','lab_hazard_severity','','danger','N','0','admin',now(3),'',null,''),
    (5010,1,'待整改','PENDING_RECTIFICATION','lab_hazard_status','','warning','Y','0','admin',now(3),'',null,''),
    (5011,2,'整改中','RECTIFYING','lab_hazard_status','','primary','N','0','admin',now(3),'',null,''),
    (5012,3,'待复查','PENDING_REVIEW','lab_hazard_status','','warning','N','0','admin',now(3),'',null,''),
    (5013,4,'已销号','CLOSED','lab_hazard_status','','success','N','0','admin',now(3),'',null,'');

insert into sys_role_menu (role_id,menu_id)
values
    ((select role_id from sys_role where role_key='lab_safety_officer'),4400),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4401),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4402),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4403),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4410),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4411),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4412),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4420),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4428),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4429),
    ((select role_id from sys_role where role_key='lab_safety_officer'),4430),
    ((select role_id from sys_role where role_key='lab_manager'),4400),
    ((select role_id from sys_role where role_key='lab_manager'),4402),
    ((select role_id from sys_role where role_key='lab_manager'),4403),
    ((select role_id from sys_role where role_key='lab_manager'),4429),
    ((select role_id from sys_role where role_key='lab_repair_worker'),4400),
    ((select role_id from sys_role where role_key='lab_repair_worker'),4403),
    ((select role_id from sys_role where role_key='lab_repair_worker'),4429);
