ALTER TABLE list_version
 ADD COLUMN title VARCHAR(100),
 ADD COLUMN request_key VARCHAR(80),
 ADD COLUMN request_hash CHAR(64),
 ADD UNIQUE KEY uk_list_revision_request(list_id,request_key);
