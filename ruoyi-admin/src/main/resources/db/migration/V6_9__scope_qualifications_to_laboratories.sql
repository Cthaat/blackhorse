-- Qualification grants are always bounded to one laboratory. Historical
-- DEVICE_CATEGORY grants were global, so each active grant is expanded across
-- all currently active laboratories while retaining the original row for one
-- laboratory as the canonical holder of its physical attachment references.
create temporary table lab_v6_9_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_9_guard (guard_key) values ('X');

insert into lab_v6_9_guard (guard_key)
select 'X'
where exists
(
    select 1
    from lab_qualification qualification
    left join lab_laboratory laboratory
      on qualification.scope_type = 'LABORATORY'
     and binary qualification.scope_id = binary cast(laboratory.id as char)
    where qualification.scope_type not in ('LABORATORY', 'DEVICE_CATEGORY')
       or (qualification.scope_type = 'LABORATORY' and laboratory.id is null)
       or (qualification.scope_type = 'DEVICE_CATEGORY' and not exists
           (select 1 from lab_laboratory active_laboratory
            where active_laboratory.del_flag = '0'))
)
   or (select cast(coalesce(max(id), 0) as decimal(65, 0))
              + cast((select count(*)
                      from lab_qualification category_qualification
                      inner join lab_laboratory active_laboratory
                        on active_laboratory.del_flag = '0'
                       and active_laboratory.id <>
                           (select min(id) from lab_laboratory where del_flag = '0')
                      where category_qualification.scope_type = 'DEVICE_CATEGORY'
                        and category_qualification.del_flag = '0') as decimal(65, 0))
       from lab_qualification) > 9223372036854775807;

create temporary table lab_v6_9_qualification_clone
(
    source_qualification_id bigint not null,
    laboratory_id bigint not null,
    clone_qualification_id bigint not null,
    primary key (source_qualification_id, laboratory_id),
    unique key uk_v6_9_clone_qualification (clone_qualification_id)
) engine=innodb;

insert into lab_v6_9_qualification_clone
    (source_qualification_id, laboratory_id, clone_qualification_id)
select clone_candidate.source_qualification_id,
       clone_candidate.laboratory_id,
       qualification_id_base.max_qualification_id + clone_candidate.clone_sequence
from
(
    select qualification.id as source_qualification_id,
           laboratory.id as laboratory_id,
           row_number() over (order by qualification.id, laboratory.id) as clone_sequence
    from lab_qualification qualification
    inner join lab_laboratory laboratory
      on laboratory.del_flag = '0'
     and laboratory.id <> (select min(id) from lab_laboratory where del_flag = '0')
    where qualification.scope_type = 'DEVICE_CATEGORY'
      and qualification.del_flag = '0'
) clone_candidate
cross join
(
    select coalesce(max(id), 0) as max_qualification_id
    from lab_qualification
) qualification_id_base;

alter table lab_qualification
    add column laboratory_id bigint null after scope_id;

update lab_qualification qualification
inner join lab_laboratory laboratory
  on qualification.scope_type = 'LABORATORY'
 and binary qualification.scope_id = binary cast(laboratory.id as char)
set qualification.laboratory_id = laboratory.id,
    qualification.update_time = qualification.update_time;

update lab_qualification qualification
cross join
(
    select min(id) as laboratory_id
    from lab_laboratory
    where del_flag = '0'
) first_laboratory
set qualification.laboratory_id = first_laboratory.laboratory_id,
    qualification.update_time = qualification.update_time
where qualification.scope_type = 'DEVICE_CATEGORY';

insert into lab_qualification
    (id, user_id, scope_type, scope_id, laboratory_id, valid_from, valid_until,
     revoked_at, revoke_reason, version, create_by, create_time, update_by,
     update_time, del_flag)
select qualification_clone.clone_qualification_id,
       qualification.user_id, qualification.scope_type, qualification.scope_id,
       qualification_clone.laboratory_id,
       qualification.valid_from, qualification.valid_until,
       qualification.revoked_at, qualification.revoke_reason,
       qualification.version, qualification.create_by, qualification.create_time,
       qualification.update_by, qualification.update_time, qualification.del_flag
from lab_qualification qualification
inner join lab_v6_9_qualification_clone qualification_clone
  on qualification_clone.source_qualification_id = qualification.id;

-- Each clone is derived from the canonical legacy global qualification kept on
-- the first active laboratory. Qualification attachments stay on that canonical
-- row: lab_attachment.storage_key uniquely names one physical file, so copying
-- only its metadata would either violate the unique key or create a missing file.
-- Status history has no such physical-file constraint and is copied to every
-- clone so each migrated qualification retains the complete audit timeline.
insert into lab_status_history
    (object_type, object_id, from_status, to_status, operator_id, reason,
     trace_id, create_time, del_flag)
select history.object_type, qualification_clone.clone_qualification_id,
       history.from_status, history.to_status, history.operator_id,
       history.reason, history.trace_id, history.create_time, history.del_flag
from lab_status_history history
inner join lab_v6_9_qualification_clone qualification_clone
  on qualification_clone.source_qualification_id = history.object_id
where history.object_type = 'QUALIFICATION';

alter table lab_qualification
    modify column laboratory_id bigint not null,
    add constraint fk_qualification_laboratory
        foreign key (laboratory_id) references lab_laboratory(id),
    add constraint ck_qualification_scope_type
        check (scope_type in ('LABORATORY', 'DEVICE_CATEGORY')),
    add constraint ck_qualification_laboratory_scope
        check (scope_type <> 'LABORATORY'
               or binary scope_id = binary cast(laboratory_id as char)),
    add index idx_lab_qualification_laboratory_scope
        (laboratory_id, scope_type, scope_id, valid_from, valid_until, revoked_at);

-- The temporary guard and clone-map tables are connection-scoped and disappear
-- with Flyway.
