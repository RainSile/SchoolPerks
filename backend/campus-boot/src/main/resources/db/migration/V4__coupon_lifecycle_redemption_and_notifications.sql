ALTER TABLE campus_coupon
 ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '',
 ADD COLUMN campus_only BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN review_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
 ADD COLUMN review_request_id VARCHAR(64) NULL,
 ADD COLUMN published_at TIMESTAMP(3) NULL,
 ADD CONSTRAINT ck_coupon_review_status CHECK(review_status IN ('NONE','PENDING','APPROVED','REJECTED'));
ALTER TABLE campus_coupon_claim
 ADD COLUMN code_expires_at TIMESTAMP(3) NULL,
 ADD COLUMN code_issued_at TIMESTAMP(3) NULL;

CREATE TABLE campus_coupon_review_request (
 id VARCHAR(64) PRIMARY KEY, coupon_id BIGINT NOT NULL, submitted_by BIGINT NOT NULL,
 content_version INT NOT NULL, content_snapshot JSON NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMP(3) NOT NULL, reviewed_at TIMESTAMP(3) NULL,
 pending_coupon_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN coupon_id ELSE NULL END) STORED,
 CONSTRAINT uk_coupon_pending_review UNIQUE(pending_coupon_id),
 CONSTRAINT uk_coupon_content_review UNIQUE(coupon_id,content_version),
 CONSTRAINT fk_coupon_request_coupon FOREIGN KEY(coupon_id) REFERENCES campus_coupon(id),
 CONSTRAINT fk_coupon_request_actor FOREIGN KEY(submitted_by) REFERENCES campus_user(id),
 CONSTRAINT ck_coupon_request_status CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 INDEX idx_coupon_review_queue(status,created_at)
) ENGINE=InnoDB;
ALTER TABLE campus_coupon ADD CONSTRAINT fk_coupon_current_request FOREIGN KEY(review_request_id) REFERENCES campus_coupon_review_request(id);

CREATE TABLE campus_coupon_review (
 id VARCHAR(64) PRIMARY KEY, request_id VARCHAR(64) NOT NULL, reviewer_id BIGINT NOT NULL,
 decision VARCHAR(24) NOT NULL, reason VARCHAR(255) NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT uk_coupon_review_request UNIQUE(request_id),
 CONSTRAINT fk_coupon_review_request FOREIGN KEY(request_id) REFERENCES campus_coupon_review_request(id),
 CONSTRAINT fk_coupon_review_actor FOREIGN KEY(reviewer_id) REFERENCES campus_user(id),
 CONSTRAINT ck_coupon_review_decision CHECK(decision IN ('APPROVED','REJECTED'))
) ENGINE=InnoDB;
CREATE TABLE campus_coupon_status_change (
 id VARCHAR(64) PRIMARY KEY, coupon_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 previous_status VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL, version INT NOT NULL,
 reason VARCHAR(255) NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT fk_coupon_status_coupon FOREIGN KEY(coupon_id) REFERENCES campus_coupon(id),
 CONSTRAINT fk_coupon_status_actor FOREIGN KEY(actor_id) REFERENCES campus_user(id)
) ENGINE=InnoDB;
CREATE TABLE campus_redemption_attempt_rate (
 operator_id BIGINT PRIMARY KEY, window_start TIMESTAMP(3) NOT NULL, attempts INT NOT NULL,
 CONSTRAINT fk_redemption_rate_actor FOREIGN KEY(operator_id) REFERENCES campus_user(id),
 CONSTRAINT ck_redemption_attempts CHECK(attempts>=0)
) ENGINE=InnoDB;

ALTER TABLE campus_outbox_event
 ADD COLUMN lease_token VARCHAR(64) NULL,
 ADD COLUMN locked_until TIMESTAMP(3) NULL,
 ADD COLUMN last_error_code VARCHAR(64) NULL;
CREATE TABLE campus_notification (
 id VARCHAR(64) PRIMARY KEY, event_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
 event_type VARCHAR(120) NOT NULL, payload JSON NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, read_at TIMESTAMP(3) NULL,
 CONSTRAINT uk_notification_event_user UNIQUE(event_id,user_id),
 CONSTRAINT fk_notification_user FOREIGN KEY(user_id) REFERENCES campus_user(id),
 INDEX idx_notification_user_created(user_id,created_at)
) ENGINE=InnoDB;

-- Read-only catalog projection. Cross-domain composition is explicit here; no foreign Mapper/write access.
CREATE VIEW campus_coupon_catalog AS
 SELECT c.*,m.name AS merchant_name,m.school_id,m.status AS merchant_status
 FROM campus_coupon c JOIN campus_merchant m ON m.id=c.merchant_id;
