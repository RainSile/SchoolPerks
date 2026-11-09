-- Supplements reuse the audited merchant application review resource.
CREATE TABLE campus_merchant_qualification_submission (
 application_id CHAR(36) PRIMARY KEY,
 merchant_id BIGINT NOT NULL,
 merchant_version INT NOT NULL,
 FOREIGN KEY (application_id) REFERENCES campus_merchant_application(id),
 FOREIGN KEY (merchant_id) REFERENCES campus_merchant(id),
 CHECK (merchant_version >= 0),
 INDEX idx_qualification_merchant (merchant_id)
);
