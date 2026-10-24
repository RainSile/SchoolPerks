-- V1-V5 are immutable. Legacy merchant coordinates have no declared CRS and are not backfilled.
ALTER TABLE campus_file_object DROP CHECK ck_file_purpose;
ALTER TABLE campus_file_object ADD CONSTRAINT ck_file_purpose_v6 CHECK(purpose IN ('MERCHANT_QUALIFICATION','COMMUNITY_IMAGE'));
CREATE TABLE campus_merchant_location (
 merchant_id BIGINT PRIMARY KEY, longitude DECIMAL(10,7) NOT NULL, latitude DECIMAL(10,7) NOT NULL,
 coordinate_system VARCHAR(16) NOT NULL, version INT NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(merchant_id) REFERENCES campus_merchant(id),
 CHECK(coordinate_system='WGS84'), CHECK(longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90)
);
CREATE TABLE campus_post (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, author_id BIGINT NOT NULL, school_id BIGINT NOT NULL,
 merchant_id BIGINT NULL, title VARCHAR(120) NOT NULL, body TEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', version INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL, published_at TIMESTAMP(3) NULL,
 FOREIGN KEY(author_id) REFERENCES campus_user(id), FOREIGN KEY(school_id) REFERENCES campus_school(id),
 FOREIGN KEY(merchant_id) REFERENCES campus_merchant(id),
 CHECK(status IN ('DRAFT','PENDING','PUBLISHED','REJECTED','HIDDEN','DELETED')),
 INDEX idx_post_author(author_id,id), INDEX idx_post_state(status,school_id,id)
);
CREATE TABLE campus_post_image (
 post_id BIGINT NOT NULL, file_id VARCHAR(64) NOT NULL, position INT NOT NULL,
 PRIMARY KEY(post_id,file_id), UNIQUE(post_id,position),
 FOREIGN KEY(post_id) REFERENCES campus_post(id), FOREIGN KEY(file_id) REFERENCES campus_file_object(id)
);
CREATE TABLE campus_post_review_request (
 id CHAR(36) PRIMARY KEY, post_id BIGINT NOT NULL, content_version INT NOT NULL, snapshot JSON NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', reviewer_id BIGINT NULL, reason VARCHAR(500) NULL,
 created_at TIMESTAMP(3) NOT NULL, reviewed_at TIMESTAMP(3) NULL,
 pending_post BIGINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN post_id ELSE NULL END) STORED,
 UNIQUE(pending_post), FOREIGN KEY(post_id) REFERENCES campus_post(id), FOREIGN KEY(reviewer_id) REFERENCES campus_user(id),
 CHECK(status IN ('PENDING','APPROVED','REJECTED')), INDEX idx_post_review(status,created_at)
);
CREATE TABLE campus_post_comment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, post_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL, body VARCHAR(2000) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 version INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE(author_id,post_id,request_key), FOREIGN KEY(post_id) REFERENCES campus_post(id), FOREIGN KEY(author_id) REFERENCES campus_user(id),
 CHECK(status IN ('PENDING','APPROVED','REJECTED','HIDDEN','DELETED')), INDEX idx_comment_post(post_id,status,id)
);
CREATE TABLE campus_post_like (
 post_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(post_id,user_id), FOREIGN KEY(post_id) REFERENCES campus_post(id), FOREIGN KEY(user_id) REFERENCES campus_user(id)
);
CREATE TABLE campus_post_report (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, post_id BIGINT NOT NULL, reporter_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'OPEN', version INT NOT NULL DEFAULT 0,
 resolution VARCHAR(500) NULL, resolver_id BIGINT NULL, created_at TIMESTAMP(3) NOT NULL, resolved_at TIMESTAMP(3) NULL,
 UNIQUE(post_id,reporter_id), FOREIGN KEY(post_id) REFERENCES campus_post(id), FOREIGN KEY(reporter_id) REFERENCES campus_user(id), FOREIGN KEY(resolver_id) REFERENCES campus_user(id),
 CHECK(status IN ('OPEN','RESOLVED','DISMISSED')), INDEX idx_report_state(status,id)
);
CREATE TABLE campus_community_moderation (
 id CHAR(36) PRIMARY KEY, resource_type VARCHAR(16) NOT NULL, resource_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL, previous_status VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL,
 reason VARCHAR(500) NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(actor_id) REFERENCES campus_user(id), INDEX idx_moderation_resource(resource_type,resource_id,created_at)
);
CREATE VIEW campus_post_catalog AS
 SELECT p.*,u.nickname AS author_nickname,m.name AS merchant_name FROM campus_post p
 JOIN campus_user u ON u.id=p.author_id LEFT JOIN campus_merchant m ON m.id=p.merchant_id
 WHERE p.status='PUBLISHED' AND u.status='ACTIVE' AND (p.merchant_id IS NULL OR m.status='APPROVED');

-- Public-only cross-module projection; no user identity, qualification or private content is indexed.
CREATE VIEW campus_search_source AS
 SELECT 'MERCHANT' AS resource_type,m.id AS resource_id,m.school_id,m.id AS merchant_id,
 m.name AS title,m.description AS body,m.created_at AS occurred_at
 FROM campus_merchant m WHERE m.status='APPROVED'
 UNION ALL SELECT 'COUPON',c.id,m.school_id,m.id,c.title,c.description,c.created_at
 FROM campus_coupon c JOIN campus_merchant m ON m.id=c.merchant_id
 WHERE c.status='PUBLISHED' AND c.published_at IS NOT NULL AND c.valid_until>UTC_TIMESTAMP(3) AND m.status='APPROVED'
 UNION ALL SELECT 'OFFER',o.id,m.school_id,m.id,o.title,o.description,o.created_at
 FROM campus_trade_offer o JOIN campus_merchant m ON m.id=o.merchant_id
 WHERE o.status='PUBLISHED' AND o.ends_at>UTC_TIMESTAMP(3) AND m.status='APPROVED'
 UNION ALL SELECT 'POST',p.id,p.school_id,p.merchant_id,p.title,p.body,p.created_at FROM campus_post_catalog p;
CREATE TABLE campus_search_document (
 resource_type VARCHAR(16) NOT NULL, resource_id BIGINT NOT NULL, school_id BIGINT NULL, merchant_id BIGINT NULL,
 title VARCHAR(120) NOT NULL DEFAULT '', body TEXT NOT NULL, source_hash CHAR(64) NULL,
 visible BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(resource_type,resource_id), CHECK(resource_type IN ('MERCHANT','COUPON','OFFER','POST'))
);
CREATE TABLE campus_search_rebuild (
 id CHAR(36) PRIMARY KEY, requested_by BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 cursor_type VARCHAR(16) NOT NULL DEFAULT '', cursor_id BIGINT NOT NULL DEFAULT 0, processed BIGINT NOT NULL DEFAULT 0,
 lease_token CHAR(36) NULL, locked_until TIMESTAMP(3) NULL, error_code VARCHAR(64) NULL,
 created_at TIMESTAMP(3) NOT NULL, completed_at TIMESTAMP(3) NULL,
 active_slot INT GENERATED ALWAYS AS(CASE WHEN status IN ('PENDING','RUNNING') THEN 1 ELSE NULL END) STORED,
 UNIQUE(active_slot), FOREIGN KEY(requested_by) REFERENCES campus_user(id),
 CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);
