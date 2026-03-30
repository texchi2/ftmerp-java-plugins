-- create_audit_table.sql
-- Run on Ubuntu host 192.168.30.3 as postgres superuser or enrolladmin
-- Usage: psql -h 192.168.30.3 -U enrolladmin -d ftm_enrollment -f create_audit_table.sql

CREATE TABLE IF NOT EXISTS ftm_wifi_audit_log (
    id           SERIAL PRIMARY KEY,
    changed_by   VARCHAR(50),
    changed_at   TIMESTAMP DEFAULT NOW(),
    employee_id  VARCHAR(20),
    field_name   VARCHAR(50),
    old_value    TEXT,
    new_value    TEXT,
    action       VARCHAR(20)
);

-- Grant permissions to enrolladmin
GRANT SELECT, INSERT ON ftm_wifi_audit_log TO enrolladmin;
GRANT USAGE, SELECT ON SEQUENCE ftm_wifi_audit_log_id_seq TO enrolladmin;

-- Verify
SELECT 'ftm_wifi_audit_log created OK' AS status;
SELECT column_name, data_type FROM information_schema.columns
    WHERE table_name = 'ftm_wifi_audit_log'
    ORDER BY ordinal_position;
