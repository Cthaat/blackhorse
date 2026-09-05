CREATE TABLE lab_message_template (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_type VARCHAR(32) NOT NULL,
 title VARCHAR(128) NOT NULL,
 content VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 operator_id BIGINT NOT NULL,
 create_time DATETIME(3) NOT NULL,
 publish_time DATETIME(3) NULL,
 KEY idx_message_template_active(event_type,status,publish_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_notification_preference (
 user_id BIGINT NOT NULL PRIMARY KEY,
 optional_reminders BOOLEAN NOT NULL DEFAULT TRUE,
 update_time DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_message_delivery (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 dedupe_key VARCHAR(128) NOT NULL,
 receiver_id BIGINT NOT NULL,
 event_type VARCHAR(32) NOT NULL,
 source_type VARCHAR(32) NOT NULL,
 source_id BIGINT NOT NULL,
 event_version BIGINT NOT NULL DEFAULT 1,
 business_type VARCHAR(32) NOT NULL,
 business_id BIGINT NOT NULL,
 template_version VARCHAR(40) NOT NULL,
 title_snapshot VARCHAR(128) NOT NULL,
 content_snapshot VARCHAR(500) NOT NULL,
 status VARCHAR(24) NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 execution_version INT NOT NULL DEFAULT 0,
 next_retry_at DATETIME(3) NULL,
 lease_until DATETIME(3) NULL,
 error_code VARCHAR(64) NULL,
 trace_id VARCHAR(64) NULL,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 UNIQUE KEY uk_message_delivery_dedupe(dedupe_key),
 KEY idx_message_delivery_due(status,next_retry_at,id),
 KEY idx_message_delivery_lease(status,lease_until,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_message_delivery_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 delivery_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 attempt_number INT NOT NULL,
 operator_id BIGINT NULL,
 reason VARCHAR(200) NULL,
 result VARCHAR(24) NOT NULL,
 error_code VARCHAR(64) NULL,
 trace_id VARCHAR(64) NULL,
 create_time DATETIME(3) NOT NULL,
 KEY idx_message_attempt_delivery(delivery_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Preserve inbox/read state and dedupe keys. The register becomes the only retry authority.
INSERT INTO lab_message_delivery(dedupe_key,receiver_id,event_type,source_type,source_id,event_version,
 business_type,business_id,template_version,title_snapshot,content_snapshot,status,attempt_count,
 execution_version,next_retry_at,error_code,create_time,update_time)
SELECT dedupe_key,receiver_id,notification_type,
 CASE WHEN dedupe_key LIKE 'history:%' THEN 'STATUS_HISTORY'
      WHEN dedupe_key LIKE 'overdue:inspection_task:%' THEN 'INSPECTION_OVERDUE'
      WHEN dedupe_key LIKE 'overdue:hazard:%' THEN 'HAZARD_OVERDUE'
      WHEN dedupe_key LIKE 'WAITLIST:%' THEN 'WAITLIST_OFFERED' ELSE 'LEGACY' END,
 CASE WHEN dedupe_key LIKE 'history:%' OR dedupe_key LIKE 'WAITLIST:%'
      THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(dedupe_key,':',2),':',-1) AS UNSIGNED)
      ELSE business_id END,
 CASE WHEN dedupe_key LIKE 'overdue:%'
      THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(dedupe_key,':',4),':',-1) AS UNSIGNED) ELSE 1 END,
 business_type,business_id,'builtin:1',title,content,
 CASE WHEN delivery_status='SENT' THEN 'DELIVERED' WHEN attempt_count>=5 THEN 'MANUAL_REQUIRED' ELSE 'RETRY_WAIT' END,
 LEAST(attempt_count,5),0,
 CASE WHEN delivery_status='FAILED' AND attempt_count<5 THEN COALESCE(next_retry_at,CURRENT_TIMESTAMP(3)) ELSE NULL END,
 CASE WHEN delivery_status='FAILED' THEN 'LEGACY_DELIVERY_ERROR' ELSE NULL END,
 create_time,COALESCE(update_time,create_time)
FROM lab_notification;

INSERT INTO lab_message_delivery_attempt(delivery_id,action,attempt_number,result,error_code,create_time)
SELECT id,'MIGRATION',attempt_count,status,error_code,CURRENT_TIMESTAMP(3) FROM lab_message_delivery;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
 (9600,'消息投递中心',2000,21,'message-center','lab/message-center/index',1,0,'C','0','0','lab:delivery:list','message','admin',NOW()),
 (9601,'投递查询',9600,1,'','',1,0,'F','0','0','lab:delivery:list','#','admin',NOW()),
 (9602,'投递重放',9600,2,'','',1,0,'F','0','0','lab:delivery:retry','#','admin',NOW()),
 (9603,'模板查询',9600,3,'','',1,0,'F','0','0','lab:message-template:list','#','admin',NOW()),
 (9604,'模板编辑发布',9600,4,'','',1,0,'F','0','0','lab:message-template:edit','#','admin',NOW()),
 (9605,'通知偏好',2000,22,'notification-preferences','lab/message-center/preferences',1,0,'C','0','0','lab:notification:list','message','admin',NOW());

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 9600 AND 9604
WHERE r.role_key='lab_system_admin' AND r.del_flag='0';
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id,9605 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.perms='lab:notification:list';
