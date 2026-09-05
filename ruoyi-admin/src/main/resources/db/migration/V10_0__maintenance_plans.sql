CREATE TABLE lab_maintenance_plan (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 device_id BIGINT NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 1,
 current_version_id BIGINT NULL,
 next_due_at DATETIME(3) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY idx_maintenance_due(enabled,next_due_at,id),
 KEY idx_maintenance_device(device_id,id),
 CONSTRAINT fk_maintenance_device FOREIGN KEY(device_id) REFERENCES lab_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_maintenance_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 plan_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 period_days INT NOT NULL,
 first_due_at DATETIME(3) NOT NULL,
 responsible_id BIGINT NOT NULL,
 description VARCHAR(1000) NULL,
 reason VARCHAR(500) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY idx_maintenance_versions(plan_id,id),
 CONSTRAINT ck_maintenance_kind CHECK(kind IN('MAINTENANCE','CALIBRATION')),
 CONSTRAINT ck_maintenance_period CHECK(period_days BETWEEN 1 AND 3650),
 CONSTRAINT fk_maintenance_version_plan FOREIGN KEY(plan_id) REFERENCES lab_maintenance_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE lab_maintenance_plan ADD CONSTRAINT fk_maintenance_current_version FOREIGN KEY(current_version_id) REFERENCES lab_maintenance_version(id);

CREATE TABLE lab_maintenance_cycle (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 plan_id BIGINT NOT NULL,
 plan_version_id BIGINT NOT NULL,
 device_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 period_days INT NOT NULL,
 responsible_id BIGINT NOT NULL,
 due_at DATETIME(3) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
 window_start DATETIME(3) NULL,
 window_end DATETIME(3) NULL,
 repair_id BIGINT NULL,
 report_attachment_id BIGINT NULL,
 completed_at DATETIME(3) NULL,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 open_plan_id BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'COMPLETED' THEN plan_id ELSE NULL END) STORED,
 UNIQUE KEY uk_maintenance_cycle_due(plan_id,due_at),
 UNIQUE KEY uk_maintenance_open_plan(open_plan_id),
 UNIQUE KEY uk_maintenance_repair(repair_id),
 KEY idx_maintenance_window(device_id,status,window_start,window_end),
 CONSTRAINT ck_maintenance_cycle_status CHECK(status IN('PLANNED','SCHEDULED','STARTED','COMPLETED')),
 CONSTRAINT ck_maintenance_window CHECK((window_start IS NULL AND window_end IS NULL) OR window_end>window_start),
 CONSTRAINT fk_maintenance_cycle_plan FOREIGN KEY(plan_id) REFERENCES lab_maintenance_plan(id),
 CONSTRAINT fk_maintenance_cycle_version FOREIGN KEY(plan_version_id) REFERENCES lab_maintenance_version(id),
 CONSTRAINT fk_maintenance_cycle_device FOREIGN KEY(device_id) REFERENCES lab_device(id),
 CONSTRAINT fk_maintenance_cycle_repair FOREIGN KEY(repair_id) REFERENCES lab_repair_order(id),
 CONSTRAINT fk_maintenance_report FOREIGN KEY(report_attachment_id) REFERENCES lab_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Retain the existing one-open-repair-per-device unique key and legacy source integrity.
-- Source IDs now have typed foreign keys; an existing fault order is never reused as a cycle.
ALTER TABLE lab_repair_order
 DROP FOREIGN KEY fk_repair_source_usage,
 DROP CHECK ck_repair_source,
 DROP CHECK ck_repair_source_id,
 ADD COLUMN usage_source_id BIGINT GENERATED ALWAYS AS (CASE WHEN source_type='ABNORMAL_RETURN' THEN source_id ELSE NULL END) STORED,
 ADD COLUMN maintenance_cycle_id BIGINT GENERATED ALWAYS AS (CASE WHEN source_type IN('MAINTENANCE','CALIBRATION') THEN source_id ELSE NULL END) STORED,
 ADD CONSTRAINT fk_repair_typed_usage FOREIGN KEY(usage_source_id) REFERENCES lab_usage_record(id),
 ADD CONSTRAINT fk_repair_maintenance_cycle FOREIGN KEY(maintenance_cycle_id) REFERENCES lab_maintenance_cycle(id),
 ADD CONSTRAINT ck_repair_source CHECK(source_type IN('ACTIVE_REPORT','ABNORMAL_RETURN','MAINTENANCE','CALIBRATION')),
 ADD CONSTRAINT ck_repair_source_id CHECK((source_type='ACTIVE_REPORT' AND source_id IS NULL) OR (source_type IN('ABNORMAL_RETURN','MAINTENANCE','CALIBRATION') AND source_id IS NOT NULL));

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
 (9900,'维护与校准',2000,27,'maintenance','lab/maintenance/index',1,0,'C','0','0','lab:maintenance:list','tool','admin',NOW()),
 (9901,'维护计划编辑',9900,1,'','',1,0,'F','0','0','lab:maintenance:edit','#','admin',NOW()),
 (9902,'安排停用窗口',9900,2,'','',1,0,'F','0','0','lab:maintenance:schedule','#','admin',NOW()),
 (9903,'启动维护周期',9900,3,'','',1,0,'F','0','0','lab:maintenance:start','#','admin',NOW());
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 9900 AND 9903 WHERE r.role_key='lab_manager' AND r.del_flag='0';
INSERT INTO sys_job(job_name,job_group,invoke_target,cron_expression,misfire_policy,concurrent,status,create_by,create_time,remark)
VALUES('维护到期周期生成','LAB','labMaintenanceJob.generate()','0 * * * * ?','2','1','0','admin',NOW(),'仅生成待安排周期，不自动停机；每设备独立事务');
