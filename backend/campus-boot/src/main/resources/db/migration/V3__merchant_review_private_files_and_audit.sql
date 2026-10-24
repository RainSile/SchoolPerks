CREATE TABLE campus_file_object (
 id VARCHAR(64) PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 purpose VARCHAR(40) NOT NULL,
 original_name VARCHAR(200) NOT NULL,
 content_type VARCHAR(100) NOT NULL,
 declared_size BIGINT NOT NULL,
 staging_key VARCHAR(512) NOT NULL,
 immutable_key VARCHAR(512) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMP(3) NOT NULL,
 expires_at TIMESTAMP(3) NOT NULL,
 completed_at TIMESTAMP(3) NULL,
 CONSTRAINT fk_file_owner FOREIGN KEY(owner_id) REFERENCES campus_user(id),
 CONSTRAINT uk_file_immutable_key UNIQUE(immutable_key),
 CONSTRAINT ck_file_size CHECK(declared_size>0),
 CONSTRAINT ck_file_purpose CHECK(purpose='MERCHANT_QUALIFICATION'),
 CONSTRAINT ck_file_status CHECK(status IN ('PENDING','READY')),
 INDEX idx_file_owner_created(owner_id,created_at),
 INDEX idx_file_pending(status,expires_at)
) ENGINE=InnoDB;

ALTER TABLE campus_merchant
 ADD COLUMN qualification_file_id VARCHAR(64) NULL,
 ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '',
 ADD COLUMN business_hours VARCHAR(255) NOT NULL DEFAULT '',
 ADD CONSTRAINT fk_merchant_qualification_file FOREIGN KEY(qualification_file_id) REFERENCES campus_file_object(id);
ALTER TABLE campus_merchant_member ADD COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

CREATE TABLE campus_merchant_application (
 id VARCHAR(64) PRIMARY KEY,
 applicant_id BIGINT NOT NULL,
 school_id BIGINT NOT NULL,
 name VARCHAR(120) NOT NULL,
 category VARCHAR(64) NOT NULL,
 address VARCHAR(255) NOT NULL,
 longitude DECIMAL(10,7) NULL,
 latitude DECIMAL(10,7) NULL,
 description VARCHAR(1000) NOT NULL DEFAULT '',
 qualification_file_id VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 merchant_id BIGINT NULL,
 review_reason VARCHAR(255) NULL,
 submitted_at TIMESTAMP(3) NOT NULL,
 reviewed_at TIMESTAMP(3) NULL,
 pending_applicant_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN applicant_id ELSE NULL END) STORED,
 CONSTRAINT uk_merchant_pending_applicant UNIQUE(pending_applicant_id),
 CONSTRAINT uk_merchant_application_store UNIQUE(merchant_id),
 CONSTRAINT fk_application_user FOREIGN KEY(applicant_id) REFERENCES campus_user(id),
 CONSTRAINT fk_application_school FOREIGN KEY(school_id) REFERENCES campus_school(id),
 CONSTRAINT fk_application_file FOREIGN KEY(qualification_file_id) REFERENCES campus_file_object(id),
 CONSTRAINT fk_application_merchant FOREIGN KEY(merchant_id) REFERENCES campus_merchant(id),
 CONSTRAINT ck_application_status CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT ck_application_coordinates CHECK((longitude IS NULL AND latitude IS NULL) OR (longitude IS NOT NULL AND latitude IS NOT NULL AND longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90)),
 INDEX idx_application_status_created(status,submitted_at),
 INDEX idx_application_owner_created(applicant_id,submitted_at)
) ENGINE=InnoDB;

CREATE TABLE campus_merchant_review (
 id VARCHAR(64) PRIMARY KEY,
 application_id VARCHAR(64) NOT NULL,
 reviewer_id BIGINT NOT NULL,
 decision VARCHAR(24) NOT NULL,
 reason VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT uk_merchant_review_application UNIQUE(application_id),
 CONSTRAINT fk_review_application FOREIGN KEY(application_id) REFERENCES campus_merchant_application(id),
 CONSTRAINT fk_review_actor FOREIGN KEY(reviewer_id) REFERENCES campus_user(id),
 CONSTRAINT ck_merchant_review_decision CHECK(decision IN ('APPROVED','REJECTED'))
) ENGINE=InnoDB;

CREATE TABLE campus_merchant_status_change (
 id VARCHAR(64) PRIMARY KEY,
 merchant_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 previous_status VARCHAR(24) NOT NULL,
 status VARCHAR(24) NOT NULL,
 reason VARCHAR(255) NOT NULL,
 version INT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT fk_status_merchant FOREIGN KEY(merchant_id) REFERENCES campus_merchant(id),
 CONSTRAINT fk_status_actor FOREIGN KEY(actor_id) REFERENCES campus_user(id),
 INDEX idx_status_merchant_created(merchant_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE campus_audit_event (
 id VARCHAR(64) PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 action VARCHAR(100) NOT NULL,
 resource_type VARCHAR(64) NOT NULL,
 resource_id VARCHAR(64) NOT NULL,
 details JSON NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT fk_audit_actor FOREIGN KEY(actor_id) REFERENCES campus_user(id),
 INDEX idx_audit_resource(resource_type,resource_id,created_at),
 INDEX idx_audit_actor(actor_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE campus_campus_verification_review (
 id VARCHAR(64) PRIMARY KEY,
 verification_id VARCHAR(64) NOT NULL,
 reviewer_id BIGINT NOT NULL,
 decision VARCHAR(24) NOT NULL,
 reason VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 CONSTRAINT uk_campus_review_verification UNIQUE(verification_id),
 CONSTRAINT fk_campus_review_application FOREIGN KEY(verification_id) REFERENCES campus_campus_verification(id),
 CONSTRAINT fk_campus_review_actor FOREIGN KEY(reviewer_id) REFERENCES campus_user(id),
 CONSTRAINT ck_campus_review_decision CHECK(decision IN ('APPROVED','REJECTED'))
) ENGINE=InnoDB;
