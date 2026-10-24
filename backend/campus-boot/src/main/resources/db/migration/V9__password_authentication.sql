-- Separate purposes prevent login codes from being exchanged for registration/password reset.
ALTER TABLE campus_verification_challenge DROP CHECK ck_challenge_purpose;
ALTER TABLE campus_verification_challenge ADD CONSTRAINT ck_challenge_purpose
  CHECK (purpose IN ('LOGIN','REGISTER','RESET_PASSWORD'));
