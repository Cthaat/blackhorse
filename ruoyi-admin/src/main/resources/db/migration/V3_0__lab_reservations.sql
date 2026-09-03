create table lab_reservation (
    id bigint not null auto_increment,
    reservation_no varchar(32) not null,
    device_id bigint not null,
    applicant_id bigint not null,
    start_time datetime(3) not null,
    end_time datetime(3) not null,
    purpose varchar(200) not null,
    remark varchar(500) null,
    status varchar(20) not null default 'PENDING',
    approval_by bigint null,
    approval_time datetime(3) null,
    approval_reason varchar(500) null,
    cancel_time datetime(3) null,
    cancel_reason varchar(500) null,
    idempotency_key varchar(64) null,
    request_hash char(64) null,
    idempotency_expires_at datetime(3) null,
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null default current_timestamp(3),
    update_by varchar(64) not null default '',
    update_time datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    unique key uk_lab_reservation_no (reservation_no),
    unique key uk_lab_reservation_idempotency (applicant_id, idempotency_key),
    key idx_lab_reservation_conflict (device_id, status, start_time, end_time, del_flag),
    key idx_lab_reservation_scope (applicant_id, status, start_time, del_flag),
    key idx_lab_reservation_idempotency_expiry (idempotency_expires_at),
    constraint fk_lab_reservation_device foreign key (device_id) references lab_device(id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, update_by, update_time, remark)
select seed.config_name, seed.config_key, seed.config_value, 'Y',
       'admin', now(3), '', null, 'V3预约与领用固定策略'
from (
    select '最小提前分钟' as config_name,
           'lab.reservation.min-lead-minutes' as config_key, '30' as config_value
    union all select '最大提前天数', 'lab.reservation.max-advance-days', '30'
    union all select '最短预约分钟', 'lab.reservation.min-duration-minutes', '30'
    union all select '最长预约分钟', 'lab.reservation.max-duration-minutes', '480'
    union all select '领用迟到分钟', 'lab.usage.checkout.late-minutes', '15'
) seed
where not exists (
    select 1
    from sys_config current_config
    where current_config.config_key = seed.config_key
);
