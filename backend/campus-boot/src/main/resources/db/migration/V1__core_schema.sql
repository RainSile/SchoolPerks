-- MySQL 8.0.16+; InnoDB; UTC timestamps.
-- No seed accounts, destructive DDL, or statements selecting another database.
CREATE TABLE campus_school (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE campus_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NULL COMMENT 'Optional future password login; never plaintext',
  school_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_user_phone UNIQUE (phone),
  CONSTRAINT fk_user_school FOREIGN KEY (school_id) REFERENCES campus_school(id),
  CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE campus_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE campus_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(120) NOT NULL,
  name VARCHAR(64) NOT NULL,
  CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE campus_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES campus_user(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES campus_role(id)
) ENGINE=InnoDB;

CREATE TABLE campus_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES campus_role(id),
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES campus_permission(id)
) ENGINE=InnoDB;

CREATE TABLE campus_merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  school_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(64) NOT NULL,
  address VARCHAR(255) NOT NULL,
  longitude DECIMAL(10,7) NULL,
  latitude DECIMAL(10,7) NULL,
  qualification_object_key VARCHAR(512) NULL COMMENT 'Private object key, never a permanent public URL',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_merchant_owner FOREIGN KEY (owner_id) REFERENCES campus_user(id),
  CONSTRAINT fk_merchant_school FOREIGN KEY (school_id) REFERENCES campus_school(id),
  CONSTRAINT ck_merchant_status CHECK (status IN ('PENDING','APPROVED','REJECTED','SUSPENDED')),
  CONSTRAINT ck_merchant_longitude CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_merchant_latitude CHECK (latitude BETWEEN -90 AND 90),
  INDEX idx_merchant_status_id (status,id),
  INDEX idx_merchant_school_category (school_id,category,status)
) ENGINE=InnoDB;

CREATE TABLE campus_merchant_member (
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(32) NOT NULL,
  PRIMARY KEY (merchant_id,user_id),
  CONSTRAINT fk_member_merchant FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES campus_user(id)
) ENGINE=InnoDB;

CREATE TABLE campus_coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  coupon_type VARCHAR(24) NOT NULL,
  rules JSON NOT NULL COMMENT 'Versioned rules snapshot; validate by type in service',
  total_stock INT NOT NULL,
  remaining_stock INT NOT NULL,
  valid_from TIMESTAMP(3) NOT NULL,
  valid_until TIMESTAMP(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_coupon_merchant FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
  CONSTRAINT ck_coupon_stock CHECK (remaining_stock >= 0 AND remaining_stock <= total_stock),
  CONSTRAINT ck_coupon_dates CHECK (valid_until > valid_from),
  CONSTRAINT ck_coupon_type CHECK (coupon_type IN ('AMOUNT_OFF','DISCOUNT')),
  CONSTRAINT ck_coupon_status CHECK (status IN ('DRAFT','PUBLISHED','EXPIRED','OFFLINE')),
  INDEX idx_coupon_merchant_status (merchant_id,status),
  INDEX idx_coupon_expiry (status,valid_until)
) ENGINE=InnoDB;

CREATE TABLE campus_coupon_claim (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  redemption_code_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
  claimed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  redeemed_at TIMESTAMP(3) NULL,
  CONSTRAINT uk_claim_user_coupon UNIQUE (user_id,coupon_id),
  CONSTRAINT uk_claim_code UNIQUE (redemption_code_hash),
  CONSTRAINT fk_claim_coupon FOREIGN KEY (coupon_id) REFERENCES campus_coupon(id),
  CONSTRAINT fk_claim_user FOREIGN KEY (user_id) REFERENCES campus_user(id),
  CONSTRAINT ck_claim_status CHECK (status IN ('AVAILABLE','REDEEMED','EXPIRED')),
  INDEX idx_claim_user_status (user_id,status)
) ENGINE=InnoDB;

CREATE TABLE campus_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  offer_snapshot JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  expires_at TIMESTAMP(3) NOT NULL,
  paid_at TIMESTAMP(3) NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_order_no UNIQUE (order_no),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES campus_user(id),
  CONSTRAINT fk_order_merchant FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
  CONSTRAINT ck_order_amount CHECK (amount >= 0),
  CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','CANCELLED','REDEEMED','REFUNDED')),
  INDEX idx_order_timeout (status,expires_at),
  INDEX idx_order_user_created (user_id,created_at),
  INDEX idx_order_merchant_created (merchant_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE campus_redemption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  claim_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  redeemed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_redemption_claim UNIQUE (claim_id),
  CONSTRAINT fk_redemption_claim FOREIGN KEY (claim_id) REFERENCES campus_coupon_claim(id),
  CONSTRAINT fk_redemption_merchant FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
  CONSTRAINT fk_redemption_operator FOREIGN KEY (operator_id) REFERENCES campus_user(id)
) ENGINE=InnoDB;

CREATE TABLE campus_outbox_event (
  id VARCHAR(64) PRIMARY KEY,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at TIMESTAMP(3) NULL,
  INDEX idx_outbox_dispatch (status,next_attempt_at)
) ENGINE=InnoDB;

CREATE TABLE campus_consumed_event (
  consumer_name VARCHAR(120) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  consumed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (consumer_name,event_id)
) ENGINE=InnoDB;
