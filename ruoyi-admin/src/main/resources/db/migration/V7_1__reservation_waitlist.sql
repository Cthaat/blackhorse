CREATE TABLE lab_reservation_waitlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    remark VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL,
    offered_until DATETIME NULL,
    reservation_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    reason VARCHAR(200) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('WAITING','OFFERED') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_waitlist_request (applicant_id,idempotency_key),
    UNIQUE KEY uk_waitlist_active (applicant_id,device_id,start_time,end_time,active_flag),
    KEY idx_waitlist_queue (device_id,status,create_time,id),
    KEY idx_waitlist_owner (applicant_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约候补及限时邀请占位';

INSERT INTO sys_job (job_name,job_group,invoke_target,cron_expression,misfire_policy,concurrent,status,create_by,create_time,remark)
VALUES ('预约候补推进','LAB','labWaitlistJob.advance()','0/30 * * * * ?', '2','1','0','admin',SYSDATE(),'设备锁内推进候补、到期占位及通知补偿');
