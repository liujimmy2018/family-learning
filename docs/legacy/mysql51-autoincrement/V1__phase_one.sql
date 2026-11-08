-- MySQL 5.1.42 schema. JSON is validated by Java and stored as escaped JSON text.
CREATE TABLE parent_account (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, login_name VARCHAR(40) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE invite_code (
 code_hash CHAR(64) PRIMARY KEY, remaining_uses INT NOT NULL, expires_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE auth_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL,
 access_hash CHAR(64) NOT NULL UNIQUE, refresh_hash CHAR(64) NOT NULL UNIQUE,
 access_expires DATETIME NOT NULL, refresh_expires DATETIME NOT NULL,
 revoked BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(owner_id) REFERENCES parent_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE child_profile (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, nickname VARCHAR(30) NOT NULL,
 grade INT NOT NULL, textbook VARCHAR(80) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 1,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_child_owner(owner_id,deleted), FOREIGN KEY(owner_id) REFERENCES parent_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE media_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 object_key VARCHAR(100) NOT NULL UNIQUE, content_type VARCHAR(40) NOT NULL,
 size_bytes BIGINT NOT NULL, width INT NOT NULL, height INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at DATETIME,
 INDEX ix_asset_child(owner_id,child_id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE learning_material (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, type VARCHAR(30) NOT NULL, asset_ids MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE ai_job (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 material_id BIGINT, type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
 input_json MEDIUMTEXT NOT NULL, result_json MEDIUMTEXT, error_code VARCHAR(50), error_message VARCHAR(300),
 claim_token VARCHAR(36), lease_until DATETIME, attempt_count INT NOT NULL DEFAULT 0,
 next_run_at DATETIME NOT NULL,
 request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_job_request(owner_id,child_id,type,request_key),
 INDEX ix_job_queue(status,next_run_at,id), INDEX ix_job_lease(status,lease_until),
 UNIQUE KEY uk_job_claim_token(claim_token),
 FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE learning_list (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, current_version INT NOT NULL DEFAULT 1,
 material_id BIGINT, recognition_job_id BIGINT UNIQUE, request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_list_request(owner_id,child_id,request_key), INDEX ix_list_child(owner_id,child_id,id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE list_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, list_id BIGINT NOT NULL, version INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_list_version(list_id,version), FOREIGN KEY(list_id) REFERENCES learning_list(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE list_item (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, version_id BIGINT NOT NULL, ordinal INT NOT NULL,
 text VARCHAR(100) NOT NULL, meaning VARCHAR(300) NOT NULL,
 UNIQUE KEY uk_item_ordinal(version_id,ordinal), FOREIGN KEY(version_id) REFERENCES list_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE practice_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 list_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL, snapshot_json MEDIUMTEXT NOT NULL,
 settings_json MEDIUMTEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'READY',
 progress_json MEDIUMTEXT, version BIGINT NOT NULL DEFAULT 1, request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_practice_request(owner_id,child_id,request_key),
 INDEX ix_practice_child(owner_id,child_id,id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
CREATE TABLE play_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, practice_id BIGINT NOT NULL, event_id VARCHAR(36) NOT NULL,
 payload_json MEDIUMTEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_event(practice_id,event_id), FOREIGN KEY(practice_id) REFERENCES practice_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
