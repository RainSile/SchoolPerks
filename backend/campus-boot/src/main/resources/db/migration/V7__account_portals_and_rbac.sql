-- Exclusive portal identity is independent of fine-grained permissions.
ALTER TABLE campus_user
  ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'USER',
  ADD COLUMN access_version INT NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_user_account_type CHECK(account_type IN ('USER','MERCHANT','ADMIN'));

ALTER TABLE campus_role
  ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
  ADD COLUMN version INT NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_role_account_type CHECK(account_type IN ('USER','MERCHANT','ADMIN'));

-- Preserve existing grants without permitting membership to cross into platform administration.
UPDATE campus_user u SET account_type='MERCHANT'
WHERE EXISTS(SELECT 1 FROM campus_merchant_member m WHERE m.user_id=u.id);
UPDATE campus_user u SET account_type='ADMIN'
WHERE EXISTS(SELECT 1 FROM campus_user_role ur JOIN campus_role_permission rp ON rp.role_id=ur.role_id
  JOIN campus_permission p ON p.id=rp.permission_id
  WHERE ur.user_id=u.id AND p.code IN
    ('merchant:review','campus:review','coupon:review','community:moderate','search:manage',
     'audit:read','notification:manage','analytics:read','rbac:manage'));

ALTER TABLE campus_auth_session ADD COLUMN access_version INT NOT NULL DEFAULT 0;
CREATE INDEX idx_user_type_id ON campus_user(account_type,id);

-- Serialize administrative grants; normal business transactions never take this lock.
CREATE TABLE campus_rbac_lock (
  id TINYINT PRIMARY KEY,
  CONSTRAINT ck_rbac_single_lock CHECK(id=1)
) ENGINE=InnoDB;
INSERT INTO campus_rbac_lock(id) VALUES(1);

-- Permission catalogue only; no seeded roles, users, or administrators.
INSERT INTO campus_permission(code,name) VALUES
 ('rbac:manage','角色与账号授权管理'),('merchant:review','商家审核'),('campus:review','校园认证审核'),
 ('coupon:review','优惠审核'),('community:moderate','社区审核'),('search:manage','索引维护'),
 ('audit:read','审计查询'),('notification:manage','消息投递管理'),('analytics:read','经营统计')
ON DUPLICATE KEY UPDATE name=VALUES(name);
