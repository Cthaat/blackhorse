-- NEW facts only: old open and closed business objects are deliberately not backfilled.
CREATE TABLE lab_sla_rule (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,laboratory_id BIGINT NOT NULL,
 business_type VARCHAR(24) NOT NULL,risk VARCHAR(16) NOT NULL,
 response_hours INT NOT NULL,processing_hours INT NOT NULL,reason VARCHAR(500) NOT NULL,
 created_by BIGINT NOT NULL,created_at DATETIME(3) NOT NULL,builtin TINYINT(1) NOT NULL DEFAULT 0,
 builtin_key VARCHAR(100) GENERATED ALWAYS AS(CASE WHEN builtin=1 THEN CONCAT(laboratory_id,':',business_type,':',risk) ELSE NULL END) STORED,
 UNIQUE KEY uk_sla_default(builtin_key),KEY idx_sla_rules(laboratory_id,business_type,risk,id),
 CONSTRAINT fk_sla_rule_lab FOREIGN KEY(laboratory_id) REFERENCES lab_laboratory(id),
 CONSTRAINT ck_sla_rule_type CHECK(business_type IN('REPAIR','MAINTENANCE','HAZARD')),
 CONSTRAINT ck_sla_rule_risk CHECK(risk IN('LOW','MEDIUM','HIGH','MAJOR')),
 CONSTRAINT ck_sla_rule_hours CHECK(response_hours BETWEEN 1 AND 720 AND processing_hours BETWEEN response_hours AND 8760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_sla_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,object_type VARCHAR(32) NOT NULL,object_id BIGINT NOT NULL,
 business_type VARCHAR(24) NOT NULL,risk VARCHAR(16) NOT NULL,laboratory_id BIGINT NOT NULL,device_id BIGINT NULL,
 owner_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,rule_version_id BIGINT NOT NULL,response_hours INT NOT NULL,processing_hours INT NOT NULL,
 opened_at DATETIME(3) NOT NULL,response_due_at DATETIME(3) NOT NULL,processing_due_at DATETIME(3) NOT NULL,
 responded_at DATETIME(3) NULL,started_at DATETIME(3) NULL,completed_at DATETIME(3) NULL,closed_at DATETIME(3) NULL,
 paused_at DATETIME(3) NULL,pause_reason VARCHAR(500) NULL,total_paused_seconds BIGINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,baseline TINYINT(1) NOT NULL DEFAULT 0,last_checked_at DATETIME(3) NULL,
 UNIQUE KEY uk_sla_object(object_type,object_id),KEY idx_sla_scan(closed_at,last_checked_at,id),KEY idx_sla_scope(laboratory_id,owner_id,id),
 CONSTRAINT fk_sla_record_rule FOREIGN KEY(rule_version_id) REFERENCES lab_sla_rule(id),
 CONSTRAINT fk_sla_record_lab FOREIGN KEY(laboratory_id) REFERENCES lab_laboratory(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_sla_trace (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,record_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,
 reason VARCHAR(500) NOT NULL,operator_id BIGINT NOT NULL,created_at DATETIME(3) NOT NULL,
 KEY idx_sla_trace(record_id,id),CONSTRAINT fk_sla_trace_record FOREIGN KEY(record_id) REFERENCES lab_sla_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_sla_alert (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,record_id BIGINT NOT NULL,phase VARCHAR(16) NOT NULL,stage VARCHAR(16) NOT NULL,created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_sla_stage(record_id,phase,stage),CONSTRAINT fk_sla_alert_record FOREIGN KEY(record_id) REFERENCES lab_sla_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_sla_notice (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,alert_id BIGINT NOT NULL,record_id BIGINT NOT NULL,receiver_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL,content VARCHAR(1000) NOT NULL,
 UNIQUE KEY uk_sla_notice(alert_id,receiver_id),CONSTRAINT fk_sla_notice_alert FOREIGN KEY(alert_id) REFERENCES lab_sla_alert(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES (9910,'业务时效 SLA',2000,28,'sla','lab/sla/index',1,0,'C','0','0','lab:sla:list','time','admin',NOW()),
(9911,'SLA 计时管理',9910,1,'','',1,0,'F','0','0','lab:sla:manage','#','admin',NOW()),
(9912,'SLA 规则发布',9910,2,'','',1,0,'F','0','0','lab:sla:rule','#','admin',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 9910 AND 9912
WHERE r.role_key IN('lab_manager','lab_safety_officer') AND r.del_flag='0';
INSERT INTO sys_role_menu(role_id,menu_id) SELECT role_id,9910 FROM sys_role WHERE role_key IN('lab_repair_worker','lab_student','lab_teacher') AND del_flag='0';
INSERT INTO sys_job(job_name,job_group,invoke_target,cron_expression,misfire_policy,concurrent,status,create_by,create_time,remark)
VALUES('SLA 时效提醒','LAB','labSlaJob.scan()','0 * * * * ?','2','1','0','admin',NOW(),'自然小时；新事实接入，不追溯历史；只提醒不自动关闭业务');
