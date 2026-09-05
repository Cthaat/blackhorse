CREATE TABLE lab_reservation_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    definition_json JSON NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    published_by BIGINT NULL,
    published_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_device_version (device_id, version_number),
    KEY idx_rule_device_status (device_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备预约规则版本，发布后内容不可变';

ALTER TABLE lab_reservation
    ADD COLUMN rule_version_id BIGINT NULL COMMENT '采用的设备规则版本',
    ADD COLUMN rule_snapshot MEDIUMTEXT NULL COMMENT '提交时的规则与全局约束快照';
