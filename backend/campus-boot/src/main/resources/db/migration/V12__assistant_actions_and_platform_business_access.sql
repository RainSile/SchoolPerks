INSERT INTO campus_permission(code,name) VALUES('business:manage','平台跨端业务管理');
INSERT INTO campus_role_permission(role_id,permission_id)
SELECT DISTINCT r.id, target.id FROM campus_role r
JOIN campus_role_permission rp ON rp.role_id=r.id
JOIN campus_permission existing ON existing.id=rp.permission_id AND existing.code='rbac:manage'
CROSS JOIN campus_permission target
WHERE r.account_type='ADMIN' AND target.code='business:manage';

CREATE TABLE campus_assistant_tool_execution (
 turn_id VARCHAR(64) NOT NULL, ordinal INT NOT NULL,
 operation_id VARCHAR(64) NOT NULL, input_hash CHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL, response_body MEDIUMTEXT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), completed_at TIMESTAMP(3) NULL,
 PRIMARY KEY(turn_id,ordinal),
 FOREIGN KEY(turn_id) REFERENCES campus_assistant_turn(id),
 CHECK(status IN ('RUNNING','COMPLETED'))
);
