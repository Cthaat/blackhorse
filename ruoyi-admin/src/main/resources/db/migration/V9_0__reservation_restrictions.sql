-- Additive migration. No historical NO_SHOW scan or reservation cancellation.
-- Lock order: singleton admission gate -> sorted applicant rows -> device -> business row.
CREATE TABLE lab_restriction_gate (
 id BIGINT NOT NULL PRIMARY KEY,
 enabled_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO lab_restriction_gate(id,enabled_at) VALUES(1,CURRENT_TIMESTAMP(3));

CREATE TABLE lab_restriction_user_lock (
 user_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_restriction_rule (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 laboratory_id BIGINT NOT NULL,
 days INT NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 CONSTRAINT chk_restriction_rule_days CHECK(days BETWEEN 1 AND 90),
 KEY idx_restriction_rule_lab(laboratory_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO lab_restriction_rule(laboratory_id,days,reason,created_by,created_at)
SELECT id,7,'首次启用：新爽约事实默认限制7天',COALESCE(manager_id,1),CURRENT_TIMESTAMP(3)
FROM lab_laboratory WHERE del_flag='0';

CREATE TABLE lab_restriction (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 laboratory_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 source VARCHAR(16) NOT NULL,
 source_reservation_id BIGINT NULL,
 reason VARCHAR(1000) NOT NULL,
 starts_at DATETIME(3) NOT NULL,
 ends_at DATETIME(3) NOT NULL,
 revoked_at DATETIME(3) NULL,
 revoked_by BIGINT NULL,
 revoke_reason VARCHAR(1000) NULL,
 rule_version_id BIGINT NULL,
 rule_snapshot VARCHAR(1000) NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 CONSTRAINT chk_restriction_interval CHECK(ends_at>starts_at),
 CONSTRAINT chk_restriction_source CHECK((source='MANUAL' AND source_reservation_id IS NULL) OR (source='NO_SHOW' AND source_reservation_id IS NOT NULL)),
 UNIQUE KEY uk_restriction_no_show(source,source_reservation_id),
 KEY idx_restriction_guard(user_id,laboratory_id,revoked_at,ends_at),
 KEY idx_restriction_scope(laboratory_id,id),
 CONSTRAINT fk_restriction_rule FOREIGN KEY(rule_version_id) REFERENCES lab_restriction_rule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_restriction_appeal (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 restriction_id BIGINT NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 reviewer_id BIGINT NULL,
 review_reason VARCHAR(1000) NULL,
 created_at DATETIME(3) NOT NULL,
 reviewed_at DATETIME(3) NULL,
 UNIQUE KEY uk_restriction_one_appeal(restriction_id),
 CONSTRAINT chk_restriction_appeal_status CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT fk_restriction_appeal FOREIGN KEY(restriction_id) REFERENCES lab_restriction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lab_restriction_evidence (
 appeal_id BIGINT NOT NULL,
 attachment_id BIGINT NOT NULL,
 PRIMARY KEY(appeal_id,attachment_id),
 UNIQUE KEY uk_restriction_evidence(attachment_id),
 CONSTRAINT fk_restriction_evidence_appeal FOREIGN KEY(appeal_id) REFERENCES lab_restriction_appeal(id),
 CONSTRAINT fk_restriction_evidence_attachment FOREIGN KEY(attachment_id) REFERENCES lab_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
 (9800,'我的预约限制',2000,24,'my-restrictions','lab/restrictions/index',1,0,'C','0','0','lab:restriction:mine','lock','admin',NOW()),
 (9801,'提交限制申诉',9800,1,'','',1,0,'F','0','0','lab:restriction:appeal','#','admin',NOW()),
 (9810,'预约限制管理',2000,25,'restrictions','lab/restrictions/index',1,0,'C','0','0','lab:restriction:list','lock','admin',NOW()),
 (9811,'创建手动限制',9810,1,'','',1,0,'F','0','0','lab:restriction:manual','#','admin',NOW()),
 (9812,'解除预约限制',9810,2,'','',1,0,'F','0','0','lab:restriction:revoke','#','admin',NOW()),
 (9813,'发布限制规则',9810,3,'','',1,0,'F','0','0','lab:restriction:rule','#','admin',NOW()),
 (9814,'审核限制申诉',9810,4,'','',1,0,'F','0','0','lab:restriction:review','#','admin',NOW());
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id IN(9800,9801)
WHERE r.role_key IN('lab_student','lab_manager') AND r.del_flag='0';
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 9810 AND 9814
WHERE r.role_key='lab_manager' AND r.del_flag='0';
