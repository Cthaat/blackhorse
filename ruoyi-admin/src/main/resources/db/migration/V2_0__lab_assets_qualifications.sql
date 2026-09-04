create table lab_laboratory (
    id bigint not null auto_increment,
    lab_code varchar(32) not null,
    name varchar(100) not null,
    dept_id bigint not null,
    manager_id bigint not null,
    location varchar(200) not null,
    description varchar(500) null,
    status varchar(20) not null default 'ENABLED',
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null default current_timestamp(3),
    update_by varchar(64) not null default '',
    update_time datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    unique key uk_lab_laboratory_code (lab_code),
    key idx_lab_laboratory_scope (dept_id, status, del_flag)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table lab_device (
    id bigint not null auto_increment,
    asset_no varchar(64) not null,
    laboratory_id bigint not null,
    name varchar(100) not null,
    category_code varchar(32) not null,
    model varchar(100) null,
    risk_level varchar(20) not null,
    location varchar(200) not null,
    manager_id bigint not null,
    description varchar(1000) null,
    status varchar(20) not null default 'AVAILABLE',
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null default current_timestamp(3),
    update_by varchar(64) not null default '',
    update_time datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    unique key uk_lab_device_asset_no (asset_no),
    key idx_lab_device_query (laboratory_id, category_code, status, del_flag),
    constraint fk_lab_device_laboratory foreign key (laboratory_id) references lab_laboratory(id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table lab_qualification (
    id bigint not null auto_increment,
    user_id bigint not null,
    scope_type varchar(20) not null,
    scope_id varchar(64) not null,
    valid_from datetime(3) not null,
    valid_until datetime(3) not null,
    revoked_at datetime(3) null,
    revoke_reason varchar(500) null,
    version int not null default 0,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null default current_timestamp(3),
    update_by varchar(64) not null default '',
    update_time datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    key idx_lab_qualification_user_validity (user_id, valid_from, valid_until, revoked_at, del_flag),
    key idx_lab_qualification_scope (scope_type, scope_id, valid_from, valid_until, revoked_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table lab_attachment (
    id bigint not null auto_increment,
    business_type varchar(32) not null,
    business_id bigint not null,
    original_name varchar(255) not null,
    stored_name varchar(80) not null,
    mime_type varchar(100) not null,
    size bigint not null,
    storage_key varchar(255) not null,
    sha256 char(64) not null,
    create_by varchar(64) not null default '',
    create_time datetime(3) not null default current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    unique key uk_lab_attachment_storage_key (storage_key),
    key idx_lab_attachment_object (business_type, business_id, del_flag)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table lab_status_history (
    id bigint not null auto_increment,
    object_type varchar(32) not null,
    object_id bigint not null,
    from_status varchar(32) null,
    to_status varchar(32) not null,
    operator_id bigint not null,
    reason varchar(500) not null,
    trace_id varchar(64) not null,
    create_time datetime(3) not null default current_timestamp(3),
    del_flag char(1) not null default '0',
    primary key (id),
    key idx_lab_status_history_object (object_type, object_id, create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci;
