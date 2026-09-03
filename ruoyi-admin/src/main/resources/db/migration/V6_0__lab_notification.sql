create table lab_notification (
    id bigint not null auto_increment comment '通知主键',
    dedupe_key varchar(128) not null comment '业务事件去重键',
    receiver_id bigint not null comment '接收用户',
    notification_type varchar(32) not null comment '通知类型',
    title varchar(128) not null comment '标题',
    content varchar(500) not null comment '安全中文内容',
    business_type varchar(32) not null comment '业务对象类型',
    business_id bigint not null comment '业务对象主键',
    delivery_status varchar(16) not null comment '发送状态',
    attempt_count int not null default 1 comment '投递次数',
    next_retry_at datetime(3) null comment '下次补偿时间',
    last_error_code varchar(64) null comment '安全错误码',
    read_at datetime(3) null comment '阅读时间',
    create_by varchar(64) not null default 'system',
    create_time datetime(3) not null default current_timestamp(3),
    update_by varchar(64) not null default '',
    update_time datetime(3) null,
    primary key (id),
    unique key uk_lab_notification_dedupe (dedupe_key),
    key idx_lab_notification_receiver_read (receiver_id, read_at, create_time),
    key idx_lab_notification_retry (delivery_status, next_retry_at, id),
    key idx_lab_notification_business (business_type, business_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='实验室站内通知';

alter table lab_inspection_task
    add column overdue_set_at datetime(3) null comment '本轮超期首次标记时间' after overdue_flag,
    add column overdue_event_version bigint not null default 0 comment '超期事件单调版本' after overdue_set_at;

alter table lab_hazard
    add column overdue_set_at datetime(3) null comment '本轮超期首次标记时间' after overdue_flag,
    add column overdue_event_version bigint not null default 0 comment '超期事件单调版本' after overdue_set_at;

update lab_inspection_task
set overdue_set_at = coalesce(update_time, deadline_at), overdue_event_version = 1
where overdue_flag = '1' and overdue_event_version = 0;

update lab_hazard
set overdue_set_at = coalesce(update_time, deadline), overdue_event_version = 1
where overdue_flag = '1' and overdue_event_version = 0;
