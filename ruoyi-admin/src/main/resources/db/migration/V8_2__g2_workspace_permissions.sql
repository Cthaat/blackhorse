insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
values
(9640,'异步任务中心',2000,10,'task-center','lab/task-center/index',1,0,'C','0','0','lab:task:list','upload','admin',now()),
(9641,'业务导入',9640,1,'#','',1,0,'F','0','0','lab:task:import','#','admin',now()),
(9642,'业务导出',9640,2,'#','',1,0,'F','0','0','lab:task:export','#','admin',now()),
(9660,'应用运维',2000,11,'operations','lab/operations/index',1,0,'C','0','0','lab:operations:view','monitor','admin',now());
insert into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(9640,9642)
where r.role_key in('lab_manager','lab_student','lab_safety_officer','lab_repair_worker');
insert into sys_role_menu(role_id,menu_id) select role_id,9641 from sys_role where role_key='lab_manager';
insert into sys_role_menu(role_id,menu_id) select role_id,9660 from sys_role where role_key='lab_system_admin';
