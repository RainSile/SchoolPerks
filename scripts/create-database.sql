-- Execute explicitly using your local MySQL account.
-- This creates only the requested test database; Flyway owns the tables.
CREATE DATABASE IF NOT EXISTS campus_perks_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
