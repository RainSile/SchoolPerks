-- Keep existing local challenges valid; cloud-generated codes are never stored locally.
ALTER TABLE campus_verification_challenge
  MODIFY COLUMN code_hash CHAR(64) NULL,
  ADD COLUMN verifier_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN provider_reference VARCHAR(256) NULL,
  ADD COLUMN provider_scope VARCHAR(64) NULL,
  ADD CONSTRAINT ck_challenge_verifier_hash CHECK (
    (verifier_type='LOCAL' AND code_hash IS NOT NULL) OR
    (verifier_type<>'LOCAL' AND code_hash IS NULL)
  );
