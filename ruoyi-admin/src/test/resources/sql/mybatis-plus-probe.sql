DROP TABLE IF EXISTS lab_compatibility_probe;

CREATE TABLE lab_compatibility_probe
(
    id         BIGINT      NOT NULL,
    probe_name VARCHAR(64) NOT NULL,
    sort_order INT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO lab_compatibility_probe (id, probe_name, sort_order)
VALUES (1, 'probe-alpha', 10),
       (2, 'probe-bravo', 20),
       (3, 'probe-charlie', 30),
       (4, 'probe-delta', 40),
       (5, 'probe-echo', 50);
