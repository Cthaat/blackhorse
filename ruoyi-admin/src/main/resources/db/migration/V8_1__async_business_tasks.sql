create table lab_business_task (
 id bigint not null auto_increment primary key,
 owner_id bigint not null,
 kind varchar(20) not null,
 direction varchar(10) not null,
 status varchar(20) not null,
 scope_json mediumtext not null,
 filter_json text not null,
 input_key varchar(255), result_key varchar(255), error_key varchar(255),
 parent_id bigint, max_id bigint not null default 0,
 total_count int not null default 0, success_count int not null default 0,
 failure_count int not null default 0, cursor_id bigint not null default 0,
 lease_token varchar(36), lease_until datetime(3),
 error_code varchar(64), trace_id varchar(64), retention_cleaned tinyint not null default 0,
 created_at datetime(3) not null default current_timestamp(3),
 started_at datetime(3), finished_at datetime(3), expires_at datetime(3) not null,
 index ix_task_owner(owner_id,id), index ix_task_claim(status,lease_until,id)
) engine=InnoDB;
create table lab_business_task_row (
 task_id bigint not null, row_no int not null, payload_json text not null,
 status varchar(16) not null, error_code varchar(64), object_id bigint,
 primary key(task_id,row_no), index ix_task_row_status(task_id,status,row_no)
) engine=InnoDB;
create table lab_business_task_audit (
 id bigint not null auto_increment primary key, task_id bigint not null,
 actor_id bigint not null, action varchar(32) not null,
 created_at datetime(3) not null default current_timestamp(3), index ix_task_audit(task_id,id)
) engine=InnoDB;
-- A database mutex makes capacity check plus enqueue atomic, including multiple HTTP requests.
create table lab_business_task_gate (id int not null primary key) engine=InnoDB;
insert into lab_business_task_gate values(1);
create table lab_business_task_artifact (
 task_id bigint not null, storage_key varchar(255) not null,
 primary key(task_id,storage_key)
) engine=InnoDB;
