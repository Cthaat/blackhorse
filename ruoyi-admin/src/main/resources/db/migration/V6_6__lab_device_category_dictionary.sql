-- The application validates device-category qualifications against this dictionary.
-- Fail closed before persistent writes when any reserved identity is already occupied.
create temporary table lab_v6_6_seed_guard
(
    guard_key char(1) not null,
    primary key (guard_key)
) engine=memory;

insert into lab_v6_6_seed_guard (guard_key) values ('X');

insert into lab_v6_6_seed_guard (guard_key)
select 'X'
where exists (
    select 1
    from sys_dict_type
    where dict_id = 504
       or dict_type = 'lab_device_category'
)
or exists (
    select 1
    from sys_dict_data
    where dict_code between 5014 and 5023
       or dict_type = 'lab_device_category'
);

insert into sys_dict_type
    (dict_id, dict_name, dict_type, status, create_by, create_time,
     update_by, update_time, remark)
values
    (504, '实验室设备类别', 'lab_device_category', '0', 'admin', now(3),
     '', null, '实验室设备分类及资格适用范围');

insert into sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class,
     list_class, is_default, status, create_by, create_time, update_by,
     update_time, remark)
values
    (5014, 1,  '显微镜',       'MICROSCOPE',     'lab_device_category', '', 'primary', 'Y', '0', 'admin', now(3), '', null, ''),
    (5015, 2,  '光谱分析仪',   'SPECTROMETER',   'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5016, 3,  '3D打印机',     'PRINTER_3D',     'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5017, 4,  '离心机',       'CENTRIFUGE',     'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5018, 5,  '激光切割机',   'LASER_CUTTER',   'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5019, 6,  '示波器',       'OSCILLOSCOPE',   'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5020, 7,  '焊接工作台',   'WELDING_STATION','lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5021, 8,  '综合测试台',   'TEST_BENCH',     'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5022, 9,  '环境监测仪',   'ENV_MONITOR',    'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, ''),
    (5023, 10, '通风橱',       'FUME_HOOD',      'lab_device_category', '', 'primary', 'N', '0', 'admin', now(3), '', null, '');
