CREATE TABLE campus_verification_rate (
  rate_key CHAR(64) PRIMARY KEY,
  request_count INT NOT NULL,
  window_start TIMESTAMP(3) NOT NULL,
  CONSTRAINT ck_rate_count CHECK(request_count>=0)
) ENGINE=InnoDB;

CREATE TABLE campus_verification_challenge (
  id VARCHAR(64) PRIMARY KEY,
  phone VARCHAR(32) NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT ck_challenge_status CHECK(status IN ('SENT','FAILED','CONSUMED','SUPERSEDED')),
  CONSTRAINT ck_challenge_purpose CHECK(purpose='LOGIN'),
  CONSTRAINT ck_challenge_attempts CHECK(attempts>=0),
  INDEX idx_challenge_phone_status(phone,status),
  INDEX idx_challenge_expiry(expires_at)
) ENGINE=InnoDB;

CREATE TABLE campus_auth_session (
  id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  revoked_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES campus_user(id),
  INDEX idx_session_user(user_id),
  INDEX idx_session_expiry(expires_at)
) ENGINE=InnoDB;

CREATE TABLE campus_refresh_token (
  token_hash CHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  consumed_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_refresh_session FOREIGN KEY(session_id) REFERENCES campus_auth_session(id),
  INDEX idx_refresh_session(session_id),
  INDEX idx_refresh_expiry(expires_at)
) ENGINE=InnoDB;

CREATE TABLE campus_campus_verification (
  id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  school_id BIGINT NOT NULL,
  encrypted_identity TEXT NOT NULL COMMENT 'AES-256-GCM, random IV, AAD=user ID; key supplied by environment',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  submitted_at TIMESTAMP(3) NOT NULL,
  reviewed_at TIMESTAMP(3) NULL,
  review_reason VARCHAR(255) NULL,
  active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','APPROVED') THEN user_id ELSE NULL END) STORED,
  CONSTRAINT uk_verification_active_user UNIQUE(active_user_id),
  CONSTRAINT fk_verification_user FOREIGN KEY(user_id) REFERENCES campus_user(id),
  CONSTRAINT fk_verification_school FOREIGN KEY(school_id) REFERENCES campus_school(id),
  CONSTRAINT ck_verification_status CHECK(status IN ('PENDING','APPROVED','REJECTED')),
  INDEX idx_verification_user_created(user_id,submitted_at)
) ENGINE=InnoDB;
