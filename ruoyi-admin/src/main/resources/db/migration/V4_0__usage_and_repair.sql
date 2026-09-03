create table lab_usage_record (
    id bigint not null auto_increment,
    reservation_id bigint not null,
    device_id bigint not null,
    user_id bigint not null,
    checkout_operator_id bigint not null,
    checked_out_at datetime(3) not null,
    checkout_note varchar(500) null,
    returned_at datetime(3) null,
    return_operator_id bigint null,
    return_condition varchar(20) null,
    return_note varchar(500) null,
    repair_order_id bigint null,
    overdue_minutes int not null default 0,
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null,
    update_by varchar(64) not null default '',
    update_time datetime(3) null,
    del_flag char(1) not null default '0',
    open_device_id bigint generated always as (
        case when returned_at is null then device_id else null end
    ) stored,
    primary key (id),
    constraint uk_usage_reservation unique (reservation_id),
    constraint uk_usage_open_device unique (open_device_id),
    constraint fk_usage_reservation foreign key (reservation_id) references lab_reservation(id),
    constraint fk_usage_device foreign key (device_id) references lab_device(id),
    constraint ck_usage_return_condition check (
        return_condition is null or return_condition in ('NORMAL', 'DAMAGED', 'FAULT')
    ),
    index idx_usage_user_time (user_id, checked_out_at),
    index idx_usage_device_time (device_id, checked_out_at),
    index idx_usage_repair_order (repair_order_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='设备领用归还记录';

create table lab_repair_order (
    id bigint not null auto_increment,
    repair_no varchar(32) not null,
    device_id bigint not null,
    source_type varchar(24) not null,
    source_id bigint null,
    reporter_id bigint not null,
    fault_description varchar(1000) not null,
    assignee_id bigint null,
    assigned_by bigint null,
    assigned_at datetime(3) null,
    started_at datetime(3) null,
    repair_result varchar(2000) null,
    result_submitted_at datetime(3) null,
    acceptance_result varchar(16) null,
    acceptance_reason varchar(1000) null,
    accepted_by bigint null,
    accepted_at datetime(3) null,
    status varchar(24) not null,
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null,
    update_by varchar(64) not null default '',
    update_time datetime(3) null,
    del_flag char(1) not null default '0',
    open_device_id bigint generated always as (
        case when status <> 'CLOSED' then device_id else null end
    ) stored,
    primary key (id),
    constraint uk_repair_no unique (repair_no),
    constraint uk_repair_open_device unique (open_device_id),
    constraint fk_repair_device foreign key (device_id) references lab_device(id),
    constraint fk_repair_source_usage foreign key (source_id) references lab_usage_record(id),
    constraint ck_repair_source check (source_type in ('ACTIVE_REPORT', 'ABNORMAL_RETURN')),
    constraint ck_repair_source_id check (
        (source_type = 'ACTIVE_REPORT' and source_id is null)
        or (source_type = 'ABNORMAL_RETURN' and source_id is not null)
    ),
    constraint ck_repair_status check (
        status in ('WAIT_ASSIGN', 'WAIT_REPAIR', 'IN_PROGRESS', 'WAIT_ACCEPTANCE', 'CLOSED')
    ),
    constraint ck_repair_acceptance check (
        acceptance_result is null or acceptance_result in ('PASSED', 'REJECTED')
    ),
    index idx_repair_assignee_status (assignee_id, status),
    index idx_repair_device_created (device_id, create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='设备维修工单';

alter table lab_usage_record
    add constraint fk_usage_repair_order
    foreign key (repair_order_id) references lab_repair_order(id);
