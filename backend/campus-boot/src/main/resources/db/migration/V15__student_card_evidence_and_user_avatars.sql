-- Existing verification decisions remain valid; new applications require a private student-card image.
ALTER TABLE campus_file_object
 DROP CHECK ck_file_purpose_v6,
 ADD CONSTRAINT ck_file_purpose_v15 CHECK(purpose IN ('MERCHANT_QUALIFICATION','COMMUNITY_IMAGE','STUDENT_CARD','USER_AVATAR'));
ALTER TABLE campus_file_object
 DROP CHECK ck_file_status,
 ADD CONSTRAINT ck_file_status_v15 CHECK(status IN ('PENDING','READY','DELETED'));
ALTER TABLE campus_campus_verification
 ADD COLUMN student_card_file_id VARCHAR(64) NULL,
 ADD COLUMN evidence_deleted_at TIMESTAMP(3) NULL,
 ADD CONSTRAINT fk_verification_student_card FOREIGN KEY(student_card_file_id) REFERENCES campus_file_object(id),
 ADD CONSTRAINT uk_verification_student_card UNIQUE(student_card_file_id),
 ADD INDEX idx_verification_evidence_retention(status,reviewed_at,evidence_deleted_at);
CREATE TABLE campus_user_avatar (
 user_id BIGINT PRIMARY KEY,
 file_id VARCHAR(64) NULL,
 default_key VARCHAR(64) NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES campus_user(id),
 FOREIGN KEY(file_id) REFERENCES campus_file_object(id),
 CHECK((file_id IS NOT NULL AND default_key IS NULL) OR (file_id IS NULL AND default_key IS NOT NULL))
);
