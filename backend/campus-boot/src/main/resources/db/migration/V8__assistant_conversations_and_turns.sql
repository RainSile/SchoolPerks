CREATE TABLE campus_assistant_conversation (
  id VARCHAR(64) PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  account_type VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT fk_assistant_owner FOREIGN KEY(owner_id) REFERENCES campus_user(id),
  CONSTRAINT ck_assistant_portal CHECK(account_type IN ('USER','MERCHANT','ADMIN')),
  INDEX idx_assistant_owner(owner_id,created_at)
) ENGINE=InnoDB;

-- Durable command/status in MySQL, completed private conversation content in MongoDB.
CREATE TABLE campus_assistant_turn (
  id VARCHAR(64) PRIMARY KEY,
  conversation_id VARCHAR(64) NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  input_hash CHAR(64) NOT NULL,
  input_text TEXT NULL,
  attempt INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  lease_token VARCHAR(64) NULL,
  locked_until TIMESTAMP(3) NULL,
  error_code VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL,
  completed_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_turn_conversation FOREIGN KEY(conversation_id) REFERENCES campus_assistant_conversation(id),
  CONSTRAINT uk_assistant_request UNIQUE(conversation_id,request_key),
  CONSTRAINT ck_assistant_turn_status CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
  INDEX idx_turn_history(conversation_id,created_at)
) ENGINE=InnoDB;
