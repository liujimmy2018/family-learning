ALTER TABLE vocabulary_collection ADD COLUMN subject VARCHAR(7) CHARACTER SET ascii NOT NULL DEFAULT 'ENGLISH';
ALTER TABLE learning_list ADD COLUMN subject VARCHAR(7) CHARACTER SET ascii NOT NULL DEFAULT 'ENGLISH';
ALTER TABLE practice_session ADD COLUMN subject VARCHAR(7) CHARACTER SET ascii NOT NULL DEFAULT 'ENGLISH';

CREATE TABLE recitation_lesson (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 grade INT NOT NULL,
 semester VARCHAR(2) NOT NULL,
 unit_name VARCHAR(40) NOT NULL,
 title VARCHAR(100) NOT NULL,
 content TEXT NOT NULL,
 revision INT NOT NULL DEFAULT 1,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uk_recitation_import(owner_id,request_key),
 KEY ix_recitation_lesson(owner_id,deleted,grade,semester),
 FOREIGN KEY(owner_id) REFERENCES parent_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE recitation_attempt (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 child_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lesson_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 title VARCHAR(100) NOT NULL,
 grade INT NOT NULL,
 semester VARCHAR(2) NOT NULL,
 unit_name VARCHAR(40) NOT NULL,
 lesson_revision INT NOT NULL,
 result VARCHAR(12) CHARACTER SET ascii NOT NULL,
 created_at DATETIME NOT NULL,
 request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uk_recitation_attempt(owner_id,child_id,request_key),
 KEY ix_recitation_attempt(owner_id,child_id,created_at),
 FOREIGN KEY(owner_id) REFERENCES parent_account(id),
 FOREIGN KEY(child_id) REFERENCES child_profile(id),
 FOREIGN KEY(lesson_id) REFERENCES recitation_lesson(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
