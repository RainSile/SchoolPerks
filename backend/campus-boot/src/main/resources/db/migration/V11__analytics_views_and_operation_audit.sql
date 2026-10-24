-- Append-only read models: event timestamps retain their original business meaning.
CREATE VIEW campus_analytics_event AS
 SELECT c.merchant_id,cc.user_id,cc.claimed_at AS occurred_at,'CLAIM' AS kind,CAST(0 AS DECIMAL(18,2)) AS amount
 FROM campus_coupon_claim cc JOIN campus_coupon c ON c.id=cc.coupon_id
 UNION ALL SELECT r.merchant_id,cc.user_id,r.redeemed_at,'COUPON_REDEEMED',0
 FROM campus_redemption r JOIN campus_coupon_claim cc ON cc.id=r.claim_id
 UNION ALL SELECT o.merchant_id,o.user_id,o.created_at,'ORDER_CREATED',0 FROM campus_order o WHERE o.offer_id IS NOT NULL
 UNION ALL SELECT o.merchant_id,o.user_id,o.paid_at,'PAYMENT',o.amount FROM campus_order o
 WHERE o.offer_id IS NOT NULL AND o.paid_at IS NOT NULL AND o.currency='CNY'
 UNION ALL SELECT o.merchant_id,o.user_id,r.completed_at,'REFUND',r.amount
 FROM campus_trade_refund r JOIN campus_order o ON o.id=r.order_id
 WHERE o.offer_id IS NOT NULL AND o.currency='CNY' AND r.status='SUCCEEDED' AND r.completed_at IS NOT NULL
 UNION ALL SELECT o.merchant_id,o.user_id,o.redeemed_at,'ORDER_REDEEMED',0 FROM campus_order o
 WHERE o.offer_id IS NOT NULL AND o.redeemed_at IS NOT NULL;

CREATE VIEW campus_analytics_claim AS
 SELECT cc.id,cc.coupon_id,c.merchant_id,c.title,cc.user_id,cc.claimed_at,r.redeemed_at
 FROM campus_coupon_claim cc JOIN campus_coupon c ON c.id=cc.coupon_id
 LEFT JOIN campus_redemption r ON r.claim_id=cc.id;
CREATE VIEW campus_analytics_order AS
 SELECT id,merchant_id,status,created_at FROM campus_order WHERE offer_id IS NOT NULL;
CREATE VIEW campus_analytics_merchant AS
 SELECT id,school_id,name,status,created_at FROM campus_merchant;
CREATE VIEW campus_analytics_account AS
 SELECT created_at,account_type FROM campus_user;

CREATE INDEX idx_analytics_order_paid ON campus_order(paid_at,merchant_id);
CREATE INDEX idx_analytics_order_redeemed ON campus_order(redeemed_at,merchant_id);
CREATE INDEX idx_analytics_order_created ON campus_order(created_at,merchant_id);
CREATE INDEX idx_analytics_claim_time ON campus_coupon_claim(claimed_at,coupon_id);
CREATE INDEX idx_analytics_redemption_time ON campus_redemption(redeemed_at,merchant_id);
CREATE INDEX idx_analytics_refund_time ON campus_trade_refund(status,completed_at,order_id);

-- Operational results deliberately contain no request/response payload, URL, IP or credentials.
-- No actor FK: immutable security history survives later account lifecycle changes.
CREATE TABLE campus_operation_audit (
 id CHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL,
 action VARCHAR(100) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id VARCHAR(64) NOT NULL,
 outcome VARCHAR(16) NOT NULL, http_status INT NOT NULL, error_code VARCHAR(64) NULL,
 duration_ms BIGINT NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 CHECK(outcome IN ('SUCCESS','DENIED','FAILURE')),CHECK(http_status BETWEEN 100 AND 599),CHECK(duration_ms>=0),
 INDEX idx_operation_time(created_at,id),INDEX idx_operation_actor(actor_id,created_at),
 INDEX idx_operation_resource(resource_type,resource_id,created_at)
) ENGINE=InnoDB;
