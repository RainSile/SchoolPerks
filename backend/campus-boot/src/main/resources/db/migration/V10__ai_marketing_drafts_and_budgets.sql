CREATE TABLE campus_ai_usage (
  user_id BIGINT NOT NULL, usage_day DATE NOT NULL, kind VARCHAR(16) NOT NULL,
  call_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY(user_id,usage_day,kind),
  FOREIGN KEY(user_id) REFERENCES campus_user(id),
  CHECK(kind IN ('CHAT','EMBEDDING')), CHECK(call_count>=0)
) ENGINE=InnoDB;

CREATE TABLE campus_marketing_draft (
  id CHAR(36) PRIMARY KEY, merchant_id BIGINT NOT NULL, created_by BIGINT NOT NULL,
  request_key VARCHAR(64) NOT NULL, input_hash CHAR(64) NOT NULL,
  brief VARCHAR(2000) NOT NULL, tone VARCHAR(24) NOT NULL, coupon_id BIGINT NULL,
  status VARCHAR(16) NOT NULL, title VARCHAR(160) NULL, body TEXT NULL,
  source_snapshot JSON NULL, source_hash CHAR(64) NULL,
  version INT NOT NULL DEFAULT 0, attempt INT NOT NULL DEFAULT 0,
  lease_token CHAR(36) NULL, locked_until TIMESTAMP(3) NULL, error_code VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
  UNIQUE(merchant_id,created_by,request_key),
  FOREIGN KEY(merchant_id) REFERENCES campus_merchant(id),
  FOREIGN KEY(created_by) REFERENCES campus_user(id),
  FOREIGN KEY(coupon_id) REFERENCES campus_coupon(id),
  CHECK(status IN ('GENERATING','DRAFT','FAILED','ARCHIVED')),
  CHECK(tone IN ('FRIENDLY','CONCISE','LIVELY')),
  CHECK(version>=0 AND attempt>=0), INDEX idx_marketing_store(merchant_id,created_at,id)
) ENGINE=InnoDB;

-- Extra metadata does not change the existing public-source columns or expose private data.
CREATE OR REPLACE VIEW campus_search_source AS
 SELECT 'MERCHANT' AS resource_type,m.id AS resource_id,m.school_id,m.id AS merchant_id,
 m.name AS title,m.description AS body,m.created_at AS occurred_at,
 m.version AS source_version,CAST(NULL AS DATETIME(3)) AS valid_from,CAST(NULL AS DATETIME(3)) AS valid_until
 FROM campus_merchant m WHERE m.status='APPROVED'
 UNION ALL SELECT 'COUPON',c.id,m.school_id,m.id,c.title,c.description,c.created_at,c.version,c.valid_from,c.valid_until
 FROM campus_coupon c JOIN campus_merchant m ON m.id=c.merchant_id
 WHERE c.status='PUBLISHED' AND c.published_at IS NOT NULL AND c.valid_until>UTC_TIMESTAMP(3) AND m.status='APPROVED'
 UNION ALL SELECT 'OFFER',o.id,m.school_id,m.id,o.title,o.description,o.created_at,o.version,o.starts_at,o.ends_at
 FROM campus_trade_offer o JOIN campus_merchant m ON m.id=o.merchant_id
 WHERE o.status='PUBLISHED' AND o.ends_at>UTC_TIMESTAMP(3) AND m.status='APPROVED'
 UNION ALL SELECT 'POST',p.id,p.school_id,p.merchant_id,p.title,p.body,p.created_at,p.version,NULL,NULL
 FROM campus_post_catalog p;
