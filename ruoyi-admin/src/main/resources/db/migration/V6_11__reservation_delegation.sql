-- Separate the beneficiary from the authenticated operator; legacy applications were self-submitted.
alter table lab_reservation add column submitter_id bigint null after applicant_id;
update lab_reservation set submitter_id = applicant_id, update_time = update_time;
alter table lab_reservation add constraint fk_reservation_submitter
    foreign key (submitter_id) references sys_user(user_id);

insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
values
    (2322, '预约代办', 2302, 3, '#', '', null, '', 1, 0, 'F', '0', '0',
     'lab:reservation:delegate', '#', 'admin', now(3), '', null,
     '仅在管理范围内代学生提交，申请人及代办人不得审批');

insert into sys_role_menu (role_id, menu_id)
select role_id, 2322 from sys_role where role_key = 'lab_manager' and del_flag = '0';
