CREATE TABLE parent_account (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, login_name VARCHAR(40) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE invite_code (
 code_hash CHAR(64) PRIMARY KEY, remaining_uses INT NOT NULL, expires_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE auth_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL,
 access_hash CHAR(64) NOT NULL UNIQUE, refresh_hash CHAR(64) NOT NULL UNIQUE,
 access_expires DATETIME(3) NOT NULL, refresh_expires DATETIME(3) NOT NULL,
 revoked BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(owner_id) REFERENCES parent_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE child_profile (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, nickname VARCHAR(30) NOT NULL,
 grade INT NOT NULL, textbook VARCHAR(80) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 1,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX ix_child_owner(owner_id,deleted), FOREIGN KEY(owner_id) REFERENCES parent_account(id),
 CHECK(grade BETWEEN 1 AND 6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE media_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 object_key VARCHAR(100) NOT NULL UNIQUE, content_type VARCHAR(40) NOT NULL,
 size_bytes BIGINT NOT NULL, width INT NOT NULL, height INT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), expires_at DATETIME(3),
 INDEX ix_asset_child(owner_id,child_id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE learning_material (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, type VARCHAR(30) NOT NULL, asset_ids JSON NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_job (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 material_id BIGINT, type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
 input_json JSON NOT NULL, result_json JSON, error_code VARCHAR(50), error_message VARCHAR(300),
 claim_token VARCHAR(36), lease_until DATETIME(3), attempt_count INT NOT NULL DEFAULT 0,
 next_run_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_job_request(owner_id,child_id,type,request_key),
 INDEX ix_job_queue(status,next_run_at,id), INDEX ix_job_lease(status,lease_until),
 FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE learning_list (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, current_version INT NOT NULL DEFAULT 1,
 material_id BIGINT, recognition_job_id BIGINT UNIQUE, request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_list_request(owner_id,child_id,request_key), INDEX ix_list_child(owner_id,child_id,id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE list_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, list_id BIGINT NOT NULL, version INT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_list_version(list_id,version), FOREIGN KEY(list_id) REFERENCES learning_list(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE list_item (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, version_id BIGINT NOT NULL, ordinal INT NOT NULL,
 text VARCHAR(100) NOT NULL, meaning VARCHAR(300) NOT NULL,
 UNIQUE KEY uk_item_ordinal(version_id,ordinal), FOREIGN KEY(version_id) REFERENCES list_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE practice_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 list_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL, snapshot_json JSON NOT NULL,
 settings_json JSON NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'READY',
 progress_json JSON, version BIGINT NOT NULL DEFAULT 1, request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_practice_request(owner_id,child_id,request_key),
 INDEX ix_practice_child(owner_id,child_id,id), FOREIGN KEY(child_id) REFERENCES child_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE play_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, practice_id BIGINT NOT NULL, event_id VARCHAR(36) NOT NULL,
 payload_json JSON NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_event(practice_id,event_id), FOREIGN KEY(practice_id) REFERENCES practice_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
