-- Disable upstream-only frontend surfaces before their Vue components are removed.
-- The duplicate-key guard deliberately fails before any persistent write when
-- the expected upstream menu or notice identities have drifted.
create temporary table lab_v6_5_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

create temporary table lab_v6_5_expected_menu
(
    menu_id bigint not null,
    menu_name varchar(50) not null,
    parent_id bigint not null,
    path varchar(200) not null,
    component varchar(255) null,
    menu_type char(1) not null,
    perms varchar(100) null,
    primary key (menu_id)
) engine=memory;

insert into lab_v6_5_expected_menu
    (menu_id, menu_name, parent_id, path, component, menu_type, perms)
values
    (3, '系统工具', 0, 'tool', null, 'M', ''),
    (4, '若依官网', 0, 'http://ruoyi.vip', null, 'M', ''),
    (104, '岗位管理', 1, 'post', 'system/post/index', 'C', 'system:post:list'),
    (107, '通知公告', 1, 'notice', 'system/notice/index', 'C', 'system:notice:list'),
    (109, '在线用户', 2, 'online', 'monitor/online/index', 'C', 'monitor:online:list'),
    (111, '数据监控', 2, 'druid', 'monitor/druid/index', 'C', 'monitor:druid:list'),
    (112, '服务监控', 2, 'server', 'monitor/server/index', 'C', 'monitor:server:list'),
    (113, '缓存监控', 2, 'cache', 'monitor/cache/index', 'C', 'monitor:cache:list'),
    (114, '缓存列表', 2, 'cacheList', 'monitor/cache/list', 'C', 'monitor:cache:list'),
    (115, '表单构建', 3, 'build', 'tool/build/index', 'C', 'tool:build:list'),
    (116, '代码生成', 3, 'gen', 'tool/gen/index', 'C', 'tool:gen:list'),
    (117, '系统接口', 3, 'swagger', 'tool/swagger/index', 'C', 'tool:swagger:list'),
    (1020, '岗位查询', 104, '', '', 'F', 'system:post:query'),
    (1021, '岗位新增', 104, '', '', 'F', 'system:post:add'),
    (1022, '岗位修改', 104, '', '', 'F', 'system:post:edit'),
    (1023, '岗位删除', 104, '', '', 'F', 'system:post:remove'),
    (1024, '岗位导出', 104, '', '', 'F', 'system:post:export'),
    (1035, '公告查询', 107, '#', '', 'F', 'system:notice:query'),
    (1036, '公告新增', 107, '#', '', 'F', 'system:notice:add'),
    (1037, '公告修改', 107, '#', '', 'F', 'system:notice:edit'),
    (1038, '公告删除', 107, '#', '', 'F', 'system:notice:remove'),
    (1046, '在线查询', 109, '#', '', 'F', 'monitor:online:query'),
    (1047, '批量强退', 109, '#', '', 'F', 'monitor:online:batchLogout'),
    (1048, '单条强退', 109, '#', '', 'F', 'monitor:online:forceLogout'),
    (1055, '生成查询', 116, '#', '', 'F', 'tool:gen:query'),
    (1056, '生成修改', 116, '#', '', 'F', 'tool:gen:edit'),
    (1057, '生成删除', 116, '#', '', 'F', 'tool:gen:remove'),
    (1058, '导入代码', 116, '#', '', 'F', 'tool:gen:import'),
    (1059, '预览代码', 116, '#', '', 'F', 'tool:gen:preview'),
    (1060, '生成代码', 116, '#', '', 'F', 'tool:gen:code');

insert into lab_v6_5_guard (guard_key) values ('X');

insert into lab_v6_5_guard (guard_key)
select 'X'
where exists (
    select 1
      from lab_v6_5_expected_menu expected
      left join sys_menu actual
        on actual.menu_id = expected.menu_id
       and actual.menu_name = expected.menu_name
       and actual.parent_id = expected.parent_id
       and actual.path = expected.path
       and actual.component <=> expected.component
       and actual.menu_type = expected.menu_type
       and actual.perms <=> expected.perms
       and actual.visible = '0'
       and actual.status = '0'
     where actual.menu_id is null
)
   or (select count(*)
         from sys_notice
        where (notice_id = 1
               and notice_title = '温馨提醒：2018-07-01 若依新版本发布啦'
               and notice_type = '2'
               and notice_content = '新版本内容'
               and status = '0'
               and create_by = 'admin')
           or (notice_id = 2
               and notice_title = '维护通知：2018-07-01 若依系统凌晨维护'
               and notice_type = '1'
               and notice_content = '维护内容'
               and status = '0'
               and create_by = 'admin')
           or (notice_id = 3
               and notice_title = '若依开源框架介绍'
               and notice_type = '1'
               and convert(notice_content using utf8mb4) like '%RuoYi开源项目%'
               and convert(notice_content using utf8mb4) like '%http://ruoyi.vip%'
               and status = '0'
               and create_by = 'admin')) <> 3;

update sys_menu
   set visible = '1',
       status = '1',
       update_by = 'migration',
       update_time = sysdate()
 where menu_id in (select menu_id from lab_v6_5_expected_menu);

delete from sys_notice_read
 where notice_id in (1, 2, 3);

delete from sys_notice
 where (notice_id = 1 and notice_title = '温馨提醒：2018-07-01 若依新版本发布啦')
    or (notice_id = 2 and notice_title = '维护通知：2018-07-01 若依系统凌晨维护')
    or (notice_id = 3 and notice_title = '若依开源框架介绍');

-- Temporary guard tables are released automatically when Flyway closes the
-- migration connection. Keeping them session-scoped also satisfies the
-- repository rule that migrations must never contain DROP TABLE statements.
