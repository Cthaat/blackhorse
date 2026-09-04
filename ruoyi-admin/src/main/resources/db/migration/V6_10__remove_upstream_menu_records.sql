-- Permanently remove the upstream-only menu records disabled by V6.5. The
-- guard protects any record changed by an operator after that migration.
create temporary table lab_v6_10_expected_menu
(
    menu_id bigint not null,
    primary key (menu_id)
) engine=memory;

insert into lab_v6_10_expected_menu (menu_id)
values
    (3), (4), (104), (107), (109), (111), (112), (113), (114), (115),
    (116), (117), (1020), (1021), (1022), (1023), (1024), (1035),
    (1036), (1037), (1038), (1046), (1047), (1048), (1055), (1056),
    (1057), (1058), (1059), (1060);

create temporary table lab_v6_10_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_10_guard (guard_key) values ('X');

insert into lab_v6_10_guard (guard_key)
select 'X'
where (select count(*)
       from sys_menu menu_record
       inner join lab_v6_10_expected_menu expected
         on expected.menu_id = menu_record.menu_id
       where menu_record.visible = '1'
         and menu_record.status = '1'
         and menu_record.update_by = 'migration') <> 30;

insert into lab_v6_10_guard (guard_key)
select 'X'
where exists
(
    select 1
    from sys_menu child
    inner join lab_v6_10_expected_menu parent
      on parent.menu_id = child.parent_id
    where child.menu_id not in
        (3, 4, 104, 107, 109, 111, 112, 113, 114, 115, 116, 117,
         1020, 1021, 1022, 1023, 1024, 1035, 1036, 1037, 1038,
         1046, 1047, 1048, 1055, 1056, 1057, 1058, 1059, 1060)
);

delete role_menu
from sys_role_menu role_menu
inner join lab_v6_10_expected_menu expected
  on expected.menu_id = role_menu.menu_id;

delete from sys_menu
where menu_id in
    (1020, 1021, 1022, 1023, 1024, 1035, 1036, 1037, 1038,
     1046, 1047, 1048, 1055, 1056, 1057, 1058, 1059, 1060);

delete from sys_menu
where menu_id in (104, 107, 109, 111, 112, 113, 114, 115, 116, 117);

delete from sys_menu
where menu_id in (3, 4);

-- Temporary guard tables are connection-scoped and disappear with Flyway.
