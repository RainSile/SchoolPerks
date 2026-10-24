-- Append-only migration. Money is always a server-side CNY snapshot; one order buys one package.
CREATE TABLE campus_trade_offer (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 merchant_id BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL,
 description VARCHAR(2000) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 price DECIMAL(12,2) NOT NULL,
 stock_total INT NOT NULL,
 stock_available INT NOT NULL,
 starts_at TIMESTAMP(3) NOT NULL,
 ends_at TIMESTAMP(3) NOT NULL,
 group_size INT NOT NULL DEFAULT 1,
 group_duration_seconds INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 created_by BIGINT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
 FOREIGN KEY (created_by) REFERENCES campus_user(id),
 CHECK (kind IN ('REGULAR','SECKILL','GROUP')),
 CHECK (price > 0 AND stock_total > 0 AND stock_available BETWEEN 0 AND stock_total),
 CHECK (ends_at > starts_at),
 CHECK ((kind='GROUP' AND group_size >= 2 AND group_duration_seconds > 0)
     OR (kind <> 'GROUP' AND group_size=1 AND group_duration_seconds=0)),
 CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE')),
 INDEX idx_offer_store (merchant_id,id)
);
CREATE VIEW campus_trade_catalog AS
 SELECT o.*,m.name AS merchant_name FROM campus_trade_offer o
 JOIN campus_merchant m ON m.id=o.merchant_id
 WHERE o.status='PUBLISHED' AND m.status='APPROVED';

CREATE TABLE campus_trade_group (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 offer_id BIGINT NOT NULL,
 creator_id BIGINT NOT NULL,
 target_size INT NOT NULL,
 reserved_count INT NOT NULL DEFAULT 0,
 paid_count INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 expires_at TIMESTAMP(3) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY (offer_id) REFERENCES campus_trade_offer(id),
 FOREIGN KEY (creator_id) REFERENCES campus_user(id),
 CHECK (status IN ('OPEN','SUCCEEDED','FAILED')),
 CHECK (paid_count >= 0 AND paid_count <= reserved_count AND reserved_count <= target_size),
 INDEX idx_group_due (status,expires_at)
);

ALTER TABLE campus_order DROP CHECK ck_order_status;
ALTER TABLE campus_order
 ADD COLUMN offer_id BIGINT NULL,
 ADD COLUMN group_id BIGINT NULL,
 ADD COLUMN stock_released BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN redeemed_at TIMESTAMP(3) NULL,
 ADD COLUMN redemption_hash CHAR(64) NULL,
 ADD COLUMN redemption_expires_at TIMESTAMP(3) NULL,
 ADD COLUMN redemption_issued_at TIMESTAMP(3) NULL,
 ADD CONSTRAINT fk_order_offer FOREIGN KEY (offer_id) REFERENCES campus_trade_offer(id),
 ADD CONSTRAINT fk_order_group FOREIGN KEY (group_id) REFERENCES campus_trade_group(id),
 ADD CONSTRAINT chk_trade_order_status CHECK (status IN
 ('PENDING_PAYMENT','GROUP_PENDING','PAID','CANCELLED','REDEEMED','REFUND_PENDING','REFUNDED'));

CREATE TABLE campus_trade_group_member (
 group_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL UNIQUE,
 PRIMARY KEY (group_id,user_id),
 FOREIGN KEY (group_id) REFERENCES campus_trade_group(id),
 FOREIGN KEY (user_id) REFERENCES campus_user(id),
 FOREIGN KEY (order_id) REFERENCES campus_order(id)
);
CREATE TABLE campus_trade_order_request (
 user_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL,
 offer_id BIGINT NOT NULL,
 group_id BIGINT NULL,
 order_id BIGINT NOT NULL UNIQUE,
 PRIMARY KEY (user_id,request_key),
 FOREIGN KEY (user_id) REFERENCES campus_user(id),
 FOREIGN KEY (order_id) REFERENCES campus_order(id)
);
CREATE TABLE campus_trade_payment (
 id CHAR(36) PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE,
 provider_order_no VARCHAR(64) NOT NULL UNIQUE,
 provider_trade_no VARCHAR(64) NULL UNIQUE,
 status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
 next_check_at TIMESTAMP(3) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY (order_id) REFERENCES campus_order(id),
 CHECK (status IN ('CREATED','PAID','CLOSED')),
 INDEX idx_payment_check (status,next_check_at)
);
CREATE TABLE campus_trade_callback (
 notification_id VARCHAR(128) PRIMARY KEY,
 payment_id CHAR(36) NOT NULL,
 payload_hash CHAR(64) NOT NULL,
 received_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY (payment_id) REFERENCES campus_trade_payment(id)
);
CREATE TABLE campus_trade_cancellation (
 order_id BIGINT PRIMARY KEY,
 reason VARCHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY (order_id) REFERENCES campus_order(id)
);
CREATE TABLE campus_trade_refund (
 id CHAR(36) PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE,
 amount DECIMAL(12,2) NOT NULL,
 reason VARCHAR(32) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP(3) NOT NULL,
 lease_token CHAR(36) NULL,
 locked_until TIMESTAMP(3) NULL,
 last_error_code VARCHAR(40) NULL,
 created_at TIMESTAMP(3) NOT NULL,
 completed_at TIMESTAMP(3) NULL,
 FOREIGN KEY (order_id) REFERENCES campus_order(id),
 CHECK (amount > 0),
 CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
 INDEX idx_refund_due (status,next_attempt_at)
);
