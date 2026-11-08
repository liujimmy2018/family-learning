-- Run as a MySQL administrator on the local development instance.
-- Replace the password below BEFORE executing; use the same password in application-local.yml.
-- This creates the database and local application user. Flyway creates application tables.
CREATE DATABASE IF NOT EXISTS family_learning_uuid
  CHARACTER SET utf8 COLLATE utf8_general_ci;

CREATE USER 'family'@'localhost'
  IDENTIFIED BY 'CHANGE_ME_to_your_own_local_password';

GRANT ALL PRIVILEGES ON family_learning_uuid.* TO 'family'@'localhost';

-- CREATE USER is for first-time setup. If family@localhost exists, skip CREATE USER,
-- run GRANT only, and use the existing user's password in the application configuration.
-- Application tables are in server/src/main/resources/db/mysql51-uuid/V*.sql.
-- Do not execute those migrations manually before a normal Flyway-managed first startup.
